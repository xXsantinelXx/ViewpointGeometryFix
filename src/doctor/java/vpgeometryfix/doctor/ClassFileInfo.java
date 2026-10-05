package vpgeometryfix.doctor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal reader for the public JVM class-file format (JVMS chapter 4):
 * class name, super class, field and method names/descriptors. No bytecode,
 * no decompilation, no class loading - so it works on JARs whose
 * dependencies (game, LWJGL) are not on the classpath.
 */
public final class ClassFileInfo {
    public String name;
    public String superName;
    public int access;
    public final List<String> fields = new ArrayList<>();
    public final List<String> methods = new ArrayList<>();

    public static ClassFileInfo read(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) throw new IOException("not a class file");
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        Object[] pool = new Object[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> pool[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> {
                    in.readLong();
                    i++; // 8-byte constants take two slots
                }
                case 7 -> pool[i] = new int[] {in.readUnsignedShort()}; // Class -> name index
                case 8, 16, 19, 20 -> in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> in.readInt();
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        ClassFileInfo info = new ClassFileInfo();
        info.access = in.readUnsignedShort();
        info.name = className(pool, in.readUnsignedShort());
        info.superName = className(pool, in.readUnsignedShort());
        int interfaces = in.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) in.readUnsignedShort();
        readMembers(in, pool, info.fields);
        readMembers(in, pool, info.methods);
        return info;
    }

    private static void readMembers(DataInputStream in, Object[] pool, List<String> out) throws IOException {
        int n = in.readUnsignedShort();
        for (int i = 0; i < n; i++) {
            int access = in.readUnsignedShort();
            String name = (String) pool[in.readUnsignedShort()];
            String desc = (String) pool[in.readUnsignedShort()];
            int attrs = in.readUnsignedShort();
            for (int a = 0; a < attrs; a++) {
                in.readUnsignedShort();
                in.skipNBytes(in.readInt() & 0xFFFFFFFFL);
            }
            out.add(((access & 0x0008) != 0 ? "static " : "") + name + " " + desc);
        }
    }

    private static String className(Object[] pool, int index) {
        if (index == 0) return null;
        int[] ref = (int[]) pool[index];
        return ((String) pool[ref[0]]).replace('/', '.');
    }
}
