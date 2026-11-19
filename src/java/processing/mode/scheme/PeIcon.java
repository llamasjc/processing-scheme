package processing.mode.scheme;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.List;

/**
 * Puts an icon into a Windows .exe, so Explorer, the taskbar and shortcuts
 * show it. Pure Java, so it works from any computer and on programs built
 * elsewhere (Lua Mode's runners). The .exe gets a new resource section with
 * the icon (RT_ICON images plus the RT_GROUP_ICON that lists them), and the
 * resource directory points at it. Whatever resources the program had, like
 * a manifest, are copied into the new section.
 */
final class PeIcon {
  private PeIcon() {}

  private static final int RT_ICON = 3, RT_GROUP_ICON = 14, RT_MANIFEST = 24;

  /** A resource to write: type, id, data. */
  private record Res(int type, int id, int lang, byte[] data) {}

  static void embed(File exe, List<byte[]> pngs, int[] sizes) throws IOException {
    byte[] f = Files.readAllBytes(exe.toPath());
    ByteBuffer b = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN);
    if (b.getShort(0) != 0x5A4D) throw new IOException("not a Windows program");
    int pe = b.getInt(0x3C);
    if (b.getInt(pe) != 0x00004550) throw new IOException("not a Windows program");
    int coff = pe + 4;
    int nSections = b.getShort(coff + 2) & 0xffff;
    int optSize = b.getShort(coff + 16) & 0xffff;
    int opt = coff + 20;
    boolean pe32plus = (b.getShort(opt) & 0xffff) == 0x20B;
    int sectionAlign = b.getInt(opt + 32);
    int fileAlign = b.getInt(opt + 36);
    int sizeOfHeaders = b.getInt(opt + 60);
    int dataDirs = opt + (pe32plus ? 112 : 96);
    int resDir = dataDirs + 2 * 8;
    int sec = opt + optSize;

    // the resources it has now (a manifest, say), to keep
    java.util.List<Res> keep = new java.util.ArrayList<>();
    int oldRva = b.getInt(resDir);
    if (oldRva != 0) readKeep(b, sec, nSections, oldRva, keep);

    // room for one more section header before the first section's data?
    int newHdr = sec + nSections * 40;
    if (newHdr + 40 > sizeOfHeaders) throw new IOException("no room for another section in this .exe");
    for (int i = 0; i < nSections; i++) {
      int raw = b.getInt(sec + i * 40 + 20);
      if (raw != 0 && newHdr + 40 > raw) throw new IOException("no room for another section in this .exe");
    }

    int lastEndVa = 0;
    for (int i = 0; i < nSections; i++) {
      int s = sec + i * 40;
      lastEndVa = Math.max(lastEndVa, b.getInt(s + 12) + Math.max(b.getInt(s + 8), b.getInt(s + 16)));
    }
    int rva = align(lastEndVa, sectionAlign);

    java.util.List<Res> all = new java.util.ArrayList<>();
    for (Res r : keep) if (r.type() != RT_ICON && r.type() != RT_GROUP_ICON) all.add(r);
    ByteArrayOutputStream group = new ByteArrayOutputStream();
    le16(group, 0); le16(group, 1); le16(group, pngs.size());
    for (int i = 0; i < pngs.size(); i++) {
      int sz = sizes[i];
      group.write(sz >= 256 ? 0 : sz); group.write(sz >= 256 ? 0 : sz); group.write(0); group.write(0);
      le16(group, 1); le16(group, 32); le32(group, pngs.get(i).length); le16(group, i + 1);
      all.add(new Res(RT_ICON, i + 1, 0x409, pngs.get(i)));
    }
    all.add(new Res(RT_GROUP_ICON, 1, 0x409, group.toByteArray()));
    byte[] rsrc = buildRsrc(all, rva);

    int rawSize = align(rsrc.length, fileAlign);
    int rawPtr = align(f.length, fileAlign);
    ByteBuffer out = ByteBuffer.allocate(rawPtr + rawSize).order(ByteOrder.LITTLE_ENDIAN);
    out.put(f);
    out.position(rawPtr);
    out.put(rsrc);

    byte[] name = ".rsrc2\0\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    out.position(newHdr);
    out.put(name);
    out.putInt(rsrc.length);           // VirtualSize
    out.putInt(rva);                   // VirtualAddress
    out.putInt(rawSize);               // SizeOfRawData
    out.putInt(rawPtr);                // PointerToRawData
    out.putInt(0); out.putInt(0); out.putShort((short) 0); out.putShort((short) 0);
    out.putInt(0x40000040);            // initialized data, readable
    out.putShort(coff + 2, (short) (nSections + 1));
    out.putInt(opt + 56, align(rva + rsrc.length, sectionAlign));   // SizeOfImage
    out.putInt(resDir, rva);
    out.putInt(resDir + 4, rsrc.length);
    out.putInt(opt + 64, 0);            // CheckSum: not needed for programs
    Files.write(exe.toPath(), out.array());
  }

  /** Reads the existing resource tree (type / name / language), by id only. */
  private static void readKeep(ByteBuffer b, int sec, int n, int rva, java.util.List<Res> keep) {
    try {
      int base = rvaToOff(b, sec, n, rva);
      for (int[] t : entries(b, base, base)) {
        if ((t[1] & 0x80000000) == 0) continue;
        int nameDir = base + (t[1] & 0x7fffffff);
        for (int[] nm : entries(b, base, nameDir)) {
          if ((nm[1] & 0x80000000) == 0) continue;
          int langDir = base + (nm[1] & 0x7fffffff);
          for (int[] lg : entries(b, base, langDir)) {
            int de = base + lg[1];
            int dataRva = b.getInt(de), size = b.getInt(de + 4);
            int off = rvaToOff(b, sec, n, dataRva);
            byte[] d = new byte[size];
            b.get(off, d);
            if (t[0] >= 0 && nm[0] >= 0) keep.add(new Res(t[0], nm[0], lg[0], d));
          }
        }
      }
    } catch (RuntimeException e) {
      // a resource tree we can't read: start a new one (the icon is what matters)
      keep.clear();
    }
  }

  /** {id or -1 for named, offset} for each entry of a resource directory. */
  private static java.util.List<int[]> entries(ByteBuffer b, int base, int dir) {
    int named = b.getShort(dir + 12) & 0xffff, ids = b.getShort(dir + 14) & 0xffff;
    java.util.List<int[]> out = new java.util.ArrayList<>();
    for (int i = 0; i < named + ids; i++) {
      int e = dir + 16 + i * 8;
      int nameField = b.getInt(e);
      out.add(new int[] { (nameField & 0x80000000) != 0 ? -1 : nameField, b.getInt(e + 4) });
    }
    return out;
  }

  private static int rvaToOff(ByteBuffer b, int sec, int n, int rva) {
    for (int i = 0; i < n; i++) {
      int s = sec + i * 40;
      int va = b.getInt(s + 12), vs = Math.max(b.getInt(s + 8), b.getInt(s + 16));
      if (rva >= va && rva < va + vs) return rva - va + b.getInt(s + 20);
    }
    throw new IllegalArgumentException("rva outside the sections");
  }

  /** The .rsrc layout: type directory, id directories, language directories, data entries, data. */
  private static byte[] buildRsrc(java.util.List<Res> all, int rva) {
    java.util.TreeMap<Integer, java.util.TreeMap<Integer, java.util.List<Res>>> tree = new java.util.TreeMap<>();
    for (Res r : all) tree.computeIfAbsent(r.type(), k -> new java.util.TreeMap<>())
      .computeIfAbsent(r.id(), k -> new java.util.ArrayList<>()).add(r);

    // sizes of each level, to place everything
    int dirs = 16 + tree.size() * 8;
    for (var t : tree.values()) {
      dirs += 16 + t.size() * 8;
      for (var langs : t.values()) dirs += 16 + langs.size() * 8;
    }
    int entriesAt = dirs;
    int dataAt = entriesAt + all.size() * 16;

    ByteBuffer o = ByteBuffer.allocate(dataAt + all.stream().mapToInt(r -> align(r.data().length, 8)).sum())
      .order(ByteOrder.LITTLE_ENDIAN);
    int[] next = { 16 + tree.size() * 8 };
    int[] entry = { entriesAt };
    int[] data = { dataAt };
    writeDir(o, 0, tree.size());
    int ti = 0;
    for (var t : tree.entrySet()) {
      int nameDir = next[0];
      next[0] += 16 + t.getValue().size() * 8;
      o.putInt(16 + ti * 8, t.getKey());
      o.putInt(16 + ti * 8 + 4, 0x80000000 | nameDir);
      writeDir(o, nameDir, t.getValue().size());
      int ni = 0;
      for (var nm : t.getValue().entrySet()) {
        int langDir = next[0];
        next[0] += 16 + nm.getValue().size() * 8;
        o.putInt(nameDir + 16 + ni * 8, nm.getKey());
        o.putInt(nameDir + 16 + ni * 8 + 4, 0x80000000 | langDir);
        writeDir(o, langDir, nm.getValue().size());
        int li = 0;
        for (Res r : nm.getValue()) {
          o.putInt(langDir + 16 + li * 8, r.lang());
          o.putInt(langDir + 16 + li * 8 + 4, entry[0]);
          o.putInt(entry[0], rva + data[0]);
          o.putInt(entry[0] + 4, r.data().length);
          o.putInt(entry[0] + 8, 0);
          o.putInt(entry[0] + 12, 0);
          o.position(data[0]);
          o.put(r.data());
          entry[0] += 16;
          data[0] += align(r.data().length, 8);
          li++;
        }
        ni++;
      }
      ti++;
    }
    return o.array();
  }

  private static void writeDir(ByteBuffer o, int at, int idEntries) {
    o.putInt(at, 0); o.putInt(at + 4, 0); o.putShort(at + 8, (short) 0); o.putShort(at + 10, (short) 0);
    o.putShort(at + 12, (short) 0); o.putShort(at + 14, (short) idEntries);
  }

  private static int align(int v, int a) { return (v + a - 1) / a * a; }
  private static void le16(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >> 8) & 0xff); }
  private static void le32(ByteArrayOutputStream o, int v) { le16(o, v & 0xffff); le16(o, (v >> 16) & 0xffff); }
}
