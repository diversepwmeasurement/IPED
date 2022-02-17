/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.Deflater;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.lucene.util.IOUtils;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.microsoft.POIFSContainerDetector;

import dpf.sp.gpinf.indexer.localization.Messages;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.BaseCarveTask;
import iped3.IItem;
import iped3.sleuthkit.ISleuthKitItem;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class Util {

    public static final Integer MIN_JAVA_VER = 8;
    public static final Integer MAX_JAVA_VER = 11;

    // These java versions have a WebView bug that crashes the JVM: JDK-8196011
    private static final String[] buggedVersions = { "1.8.0_161", "1.8.0_162", "1.8.0_171" };

    public static void fsync(Path path) throws IOException {
        IOUtils.fsync(path, false);
    }

    public static String getJavaVersionWarn() {
        String versionStr = System.getProperty("java.version"); //$NON-NLS-1$
        if (versionStr.startsWith("1.")) //$NON-NLS-1$
            versionStr = versionStr.substring(2, 3);
        int dotIdx = versionStr.indexOf("."); //$NON-NLS-1$
        if (dotIdx > -1)
            versionStr = versionStr.substring(0, dotIdx);
        Integer version = Integer.valueOf(versionStr);

        if (version < MIN_JAVA_VER) {
            return Messages.getString("JavaVersion.Error").replace("{}", MIN_JAVA_VER.toString()); //$NON-NLS-1$
        }
        if (version > MAX_JAVA_VER) {
            return Messages.getString("JavaVersion.Warn").replace("{}", version.toString()); //$NON-NLS-1$
        }
        for (String ver : buggedVersions) {
            if (System.getProperty("java.version").equals(ver))
                return Messages.getString("JavaVersion.Bug").replace("{1}", ver).replace("{2}", //$NON-NLS-1$
                        MAX_JAVA_VER.toString());
        }

        if (!System.getProperty("os.arch").contains("64"))
            return Messages.getString("JavaVersion.Arch"); //$NON-NLS-1$

        return null;
    }

    public static boolean isJavaFXPresent() {
        try {
            Class.forName("javafx.application.Platform");
            return true;

        } catch (Throwable t) {
            return false;
        }
    }

    public static String getRootName(String path) {
        int fromIndex = path.charAt(0) == '/' || path.charAt(0) == '\\' ? 1 : 0;
        int slashIdx = path.indexOf('/', fromIndex);
        int backSlashIndx = path.indexOf('\\', fromIndex);
        int expanderIdx = path.indexOf(">>", fromIndex);
        if (slashIdx == -1) {
            slashIdx = path.length();
        }
        if (backSlashIndx == -1) {
            backSlashIndx = path.length();
        }
        if (expanderIdx == -1) {
            expanderIdx = path.length();
        }
        int endIndex = Math.min(slashIdx, Math.min(backSlashIndx, expanderIdx));
        return path.substring(fromIndex, endIndex);
    }

    public static String getTrackID(IItem item) {
        String id = (String) item.getExtraAttribute(IndexItem.TRACK_ID);
        if (id == null) {
            return generateTrackID(item);
        }
        return id;
    }

    public static String generatetrackIDForTextFrag(String trackID, int fragNum) {
        if (fragNum != 0) {
            StringBuilder sb = new StringBuilder(trackID);
            sb.append("fragNum").append(fragNum);
            trackID = DigestUtils.md5Hex(sb.toString());
        }
        return trackID;
    }

    public static String getParentPath(IItem item) {
        String path = item.getPath();
        int end = path.length() - item.getName().length() - 1;
        if (end <= 0)
            return "";
        if (path.charAt(end) == '>' && path.charAt(end - 1) == '>')
            end--;
        return path.substring(0, end);
    }

    public static void main(String[] args) {
        // String str =
        // "c9b4ba73e4bbe104cb8e4d867666e77fidInDataSourcenullpathunalloc-recover\\Unalloc_1120655_314574032896_317655183360-Frag2>>Unalloc_1120655_314574032896_317655183360-Frag2_4";
        String str = "eccafd1b4a5e82f884afcaff1bd9d548fragNum1";
        System.out.println(DigestUtils.md5Hex(str));
    }

    private static String generateTrackID(IItem item) {
        StringBuilder sb = new StringBuilder();
        String notFoundIn = " not found in ";
        if (!item.isCarved() && !item.isSubItem() && item.getExtraAttribute(BaseCarveTask.FILE_FRAGMENT) == null) {
            if (item.getIdInDataSource() != null) {
                sb.append(IndexItem.ID_IN_SOURCE).append(item.getIdInDataSource());
            } else if (item instanceof ISleuthKitItem && ((ISleuthKitItem) item).getSleuthId() != null) {
                sb.append(IndexItem.ID_IN_SOURCE).append(((ISleuthKitItem) item).getSleuthId());
            } else if (!item.isQueueEnd()) {
                throw new IllegalArgumentException(IndexItem.ID_IN_SOURCE + notFoundIn + item.getPath());
            }
        } else {
            String parenttrackID = (String) item.getExtraAttribute(IndexItem.PARENT_TRACK_ID);
            if (parenttrackID != null) {
                sb.append(IndexItem.PARENT_TRACK_ID).append(parenttrackID);
            } else {
                throw new IllegalArgumentException(IndexItem.PARENT_TRACK_ID + notFoundIn + item.getPath());
            }
        }
        if (item.isSubItem()) {
            if (item.getSubitemId() != null) {
                sb.append(IndexItem.SUBITEMID).append(item.getSubitemId());
            } else {
                throw new IllegalArgumentException(IndexItem.SUBITEMID + notFoundIn + item.getPath());
            }
        }
        if (item.isCarved()) {
            Object carvedId = item.getExtraAttribute(BaseCarveTask.CARVED_ID);
            if (carvedId != null) {
                sb.append(BaseCarveTask.CARVED_ID).append(carvedId.toString());
            } else {
                throw new IllegalArgumentException(BaseCarveTask.CARVED_ID + notFoundIn + item.getPath());
            }
        }
        if (item.getPath() != null) {
            sb.append(IndexItem.PATH).append(item.getPath());
        } else {
            throw new IllegalArgumentException(IndexItem.PATH + notFoundIn + item.getPath());
        }
        String trackId = DigestUtils.md5Hex(sb.toString());
        item.setExtraAttribute(IndexItem.TRACK_ID, trackId);

        // additionally compute a globalId see #784
        if (item.getDataSource() != null) {
            sb.append(BasicProps.EVIDENCE_UUID).append(item.getDataSource().getUUID());
            String globalId = DigestUtils.md5Hex(sb.toString());
            item.setExtraAttribute(ExtraProperties.GLOBAL_ID, globalId);
        } else if (!item.isQueueEnd()) {
            throw new RuntimeException(BasicProps.EVIDENCE_UUID + notFoundIn + item.getPath());
        }

        return trackId;
    }

    public static String readUTF8Content(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        // BOM test
        if (bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            bytes[0] = bytes[1] = bytes[2] = 0;
        }
        String content = new String(bytes, "UTF-8"); //$NON-NLS-1$
        return content;
    }

    public static boolean isPhysicalDrive(File file) {
        return file.getName().toLowerCase().startsWith("physicaldrive") //$NON-NLS-1$
                || file.getAbsolutePath().toLowerCase().startsWith("/dev/"); //$NON-NLS-1$
    }

    public static File getResolvedFile(String prefix, String suffix) {
        suffix = suffix.replace('\\', File.separatorChar).replace('/', File.separatorChar);
        File file = new File(suffix);
        if (file.isAbsolute())
            return file;
        else {
            prefix = prefix.replace('\\', File.separatorChar).replace('/', File.separatorChar);
            return new File(prefix, suffix);
        }
    }

    public static String getRelativePath(File baseFile, URI uri) {
        try {
            return Util.getRelativePath(baseFile, Paths.get(uri).toFile());
        } catch (FileSystemNotFoundException e) {
            return uri.toString();
        }
    }

    public static String getRelativePath(File baseFile, File file) {
        Path base = baseFile.getParentFile().toPath().normalize().toAbsolutePath();
        Path path = file.toPath().normalize().toAbsolutePath();
        if (!base.getRoot().equals(path.getRoot()))
            return file.getAbsolutePath();
        return base.relativize(path).toString();
    }

    public static void writeObject(Object obj, String filePath) throws IOException {
        FileOutputStream fileOut = new FileOutputStream(new File(filePath));
        BufferedOutputStream bufOut = new BufferedOutputStream(fileOut);
        ObjectOutputStream out = new ObjectOutputStream(bufOut);
        out.writeObject(obj);
        out.close();
    }

    public static Object readObject(String filePath) throws IOException, ClassNotFoundException {
        FileInputStream fileIn = new FileInputStream(new File(filePath));
        BufferedInputStream bufIn = new BufferedInputStream(fileIn);
        ObjectInputStream in = new ObjectInputStream(bufIn);
        Object result;
        try {
            result = in.readObject();
        } finally {
            in.close();
        }
        return result;
    }

    public static String concatStrings(List<String> strings) {
        if (strings == null) {
            return null;
        }
        if (strings.isEmpty()) {
            return "";
        }
        if (strings.size() == 1) {
            return strings.get(0);
        }
        return strings.stream().collect(Collectors.joining(" | "));
    }

    public static String getNameWithTrueExt(IItem item) {
        String ext = item.getTypeExt();
        String name = item.getName();
        if (ext == null)
            return name;
        ext = "." + ext.toLowerCase();
        if (name.toLowerCase().endsWith(ext))
            return name;
        else
            return name + ext;
    }

    public static String concat(String filename, int num) {
        int extIndex = filename.lastIndexOf('.');
        if (extIndex == -1) {
            return filename + " (" + num + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        } else {
            String ext = filename.substring(extIndex);
            return filename.substring(0, filename.length() - ext.length()) + " (" + num + ")" + ext; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    public static String removeNonLatin1Chars(String filename) {
        StringBuilder str = new StringBuilder();
        for (char c : filename.toCharArray())
            if ((c >= '\u0020' && c <= '\u007E') || (c >= '\u00A0' && c <= '\u00FF'))
                str.append(c);
        return str.toString();
    }

    public static String getValidFilename(String filename) {
        return IOUtil.getValidFilename(filename);
    }

    public static void changeEncoding(File file) throws IOException {
        if (file.isDirectory()) {
            String[] names = file.list();
            for (int i = 0; i < names.length; i++) {
                File subFile = new File(file, names[i]);
                changeEncoding(subFile);
            }
        } else {
            Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "windows-1252")); //$NON-NLS-1$
            String contents = ""; //$NON-NLS-1$
            char[] buf = new char[(int) file.length()];
            int count;
            while ((count = reader.read(buf)) != -1) {
                contents += new String(buf, 0, count);
            }

            reader.close();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")); //$NON-NLS-1$
            writer.write(contents);
            writer.close();
        }

    }

    public static void readFile(File origem) throws IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(origem));
        byte[] buf = new byte[1024 * 1024];
        while (in.read(buf) != -1)
            ;
        in.close();
    }

    public static ArrayList<String> loadKeywords(String filePath, String encoding) throws IOException {
        ArrayList<String> array = new ArrayList<String>();
        File file = new File(filePath);
        if (!file.exists()) {
            return array;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                array.add(line.trim());
            }
        }
        reader.close();
        return array;
    }

    public static void saveKeywords(ArrayList<String> keywords, String filePath, String encoding) throws IOException {
        File file = new File(filePath);
        file.delete();
        file.createNewFile();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
        for (int i = 0; i < keywords.size(); i++) {
            writer.write(keywords.get(i) + "\r\n"); //$NON-NLS-1$
        }
        writer.close();
    }

    public static TreeSet<String> loadKeywordSet(String filePath, String encoding) throws IOException {
        TreeSet<String> set = new TreeSet<String>();
        File file = new File(filePath);
        if (!file.exists()) {
            return set;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                set.add(line.trim());
            }
        }
        reader.close();
        return set;
    }

    public static void saveKeywordSet(TreeSet<String> keywords, String filePath, String encoding) throws IOException {
        File file = new File(filePath);
        file.delete();
        file.createNewFile();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding));
        for (String keyword : keywords) {
            writer.write(keyword + "\r\n"); //$NON-NLS-1$
        }
        writer.close();
    }

    /*
     * public static String readFileAsString(String filePath) throws
     * java.io.IOException{ File file = new File(filePath); InputStreamReader reader
     * = new InputStreamReader(new FileInputStream(filePath), "windows-1252"); int
     * length = Integer.valueOf(Long.toString(file.length())); char[] buf = new
     * char[length]; reader.read(buf); reader.close(); return new String(buf); }
     */

    /*
     * public static String descompactarArquivo(File file, int maxSize) throws
     * Exception { BufferedInputStream stream = new BufferedInputStream( new
     * FileInputStream(file), 1000000); int size = file.length() > maxSize ? maxSize
     * : (int) file.length(); byte[] compressedData = new byte[size];
     * stream.read(compressedData); stream.close();
     * 
     * // Decompress the bytes Inflater decompressor = new Inflater();
     * decompressor.setInput(compressedData); ByteArrayOutputStream bos = new
     * ByteArrayOutputStream( compressedData.length); byte[] buf = new
     * byte[1000000]; while (!decompressor.finished()) { int count =
     * decompressor.inflate(buf); bos.write(buf, 0, count); maxSize -= count; if
     * (maxSize <= 0) break; } bos.close();
     * 
     * // return bos.toString("UTF-8"); return bos.toString("windows-1252"); }
     */

    /*
     * public static void compactarArquivo(String contents, String filePath)throws
     * Exception{ File file = new File(filePath+".compressed"); if(file.exists())
     * return; StringBuffer strbuf = new StringBuffer(contents); strbuf byte[] input
     * = contents.getBytes("UTF-8"); Deflater compressor = new Deflater();
     * compressor.setLevel(Deflater.BEST_COMPRESSION); compressor.setInput(input);
     * compressor.finish(); ByteArrayOutputStream bos = new
     * ByteArrayOutputStream(input.length); byte[] buf = new byte[1000000]; while
     * (!compressor.finished()) { int count = compressor.deflate(buf);
     * //System.out.println(count); bos.write(buf, 0, count); } bos.close();
     * FileOutputStream stream = new FileOutputStream(file);
     * stream.write(bos.toByteArray()); stream.close(); }
     */

    /*
     * public static void compactarArquivo(String contents, String exportPath)
     * throws Exception { String textPath = exportPath.replaceFirst("Export",
     * "Text") .replaceFirst("files", "Text"); File file = new File(textPath +
     * ".compressed"); if (file.exists()) file.delete(); FileOutputStream stream =
     * new FileOutputStream(file);
     * 
     * Deflater compressor = new Deflater();
     * compressor.setLevel(Deflater.BEST_COMPRESSION); int offset = 0, bufLen =
     * 1000000, len = bufLen; byte[] input, buf = new byte[bufLen]; while
     * (!compressor.finished()) { if (compressor.needsInput()) { if (offset + len >
     * contents.length()) len = contents.length() - offset; input =
     * contents.substring(offset, offset + len).getBytes( "windows-1252");
     * compressor.setInput(input); offset += len; if (offset == contents.length())
     * compressor.finish(); } int count = compressor.deflate(buf); stream.write(buf,
     * 0, count); } stream.close(); }
     */
    public static void compactarArquivo(String filePath) throws Exception {
        File file = new File(filePath + ".compressed"); //$NON-NLS-1$
        if (file.exists()) {
            return;
        }
        BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(file), 1000000);
        BufferedInputStream inStream = new BufferedInputStream(
                new FileInputStream(new File(filePath + ".extracted_text")), 1000000); //$NON-NLS-1$
        Deflater compressor = new Deflater();
        compressor.setLevel(Deflater.BEST_COMPRESSION);
        int bufLen = 1000000;
        byte[] input = new byte[bufLen], output = new byte[bufLen];
        while (!compressor.finished()) {
            if (compressor.needsInput()) {
                int len = inStream.read(input);
                if (len != -1) {
                    compressor.setInput(input, 0, len);
                } else {
                    compressor.finish();
                }
            }
            int count = compressor.deflate(output);
            outStream.write(output, 0, count);
        }
        outStream.close();
        inStream.close();
    }

    public static void decompress(File input, File output) {

    }

    /**
     * Carrega bibliotecas nativas de uma pasta, tentando adivinhar a ordem correta
     * 
     * @param libDir
     */
    public static void loadNatLibs(File libDir) {

        if (System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
            LinkedList<File> libList = new LinkedList<File>();
            for (File file : libDir.listFiles())
                if (file.getName().endsWith(".dll")) //$NON-NLS-1$
                    libList.addFirst(file);

            int fail = 0;
            while (!libList.isEmpty()) {
                File lib = libList.removeLast();
                try {
                    System.load(lib.getAbsolutePath());
                    fail = 0;

                } catch (Throwable t) {
                    libList.addFirst(lib);
                    fail++;
                    if (fail == libList.size())
                        throw t;
                }
            }
        }
    }

    public static void loadLibs(File libDir) {
        File[] subFiles = libDir.listFiles();
        for (File subFile : subFiles) {
            if (subFile.isFile()) {
                System.load(subFile.getAbsolutePath());
            }
        }
    }

    /**
     * Cria caminho completo a partir da pasta base, hash e extensao, no formato:
     * "base/0/1/01hhhhhhh.ext".
     */
    public static File getFileFromHash(File baseDir, String hash, String ext) {
        StringBuilder path = new StringBuilder();
        hash = hash.toUpperCase();
        path.append(hash.charAt(0)).append('/');
        path.append(hash.charAt(1)).append('/');
        path.append(hash).append('.').append(ext);
        File result = new File(baseDir, path.toString());
        return result;
    }

    public static File findFileFromHash(File baseDir, String hash) {
        if (hash == null) {
            return null;
        }
        hash = hash.toUpperCase();
        File hashDir = new File(baseDir, hash.charAt(0) + "/" + hash.charAt(1)); //$NON-NLS-1$
        if (hashDir.exists()) {
            for (File file : hashDir.listFiles()) {
                if (file.getName().startsWith(hash)) {
                    return file;
                }
            }
        }
        return null;
    }

    public static InputStream getPOIFSInputStream(TikaInputStream tin) throws IOException {
        POIFSContainerDetector oleDetector = new POIFSContainerDetector();
        MediaType mime = oleDetector.detect(tin, new Metadata());
        if (!MediaType.OCTET_STREAM.equals(mime) && tin.getOpenContainer() != null
                && tin.getOpenContainer() instanceof DirectoryEntry) {
            try (POIFSFileSystem fs = new POIFSFileSystem()) {
                copy((DirectoryEntry) tin.getOpenContainer(), fs.getRoot());
                LimitedByteArrayOutputStream baos = new LimitedByteArrayOutputStream();
                fs.writeFilesystem(baos);
                return new ByteArrayInputStream(baos.toByteArray());
            }
        }
        return null;
    }

    private static class LimitedByteArrayOutputStream extends ByteArrayOutputStream {

        private void checkLimit(int len) {
            int limit = 1 << 27;
            if (this.size() + len > limit) {
                throw new RuntimeException("Reached max memory limit of " + limit + " bytes.");
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            checkLimit(len);
            super.write(b, off, len);
        }

        @Override
        public void write(byte[] b) {
            this.write(b, 0, b.length);
        }

        @Override
        public void write(int b) {
            checkLimit(1);
            super.write(b);
        }
    }

    protected static void copy(DirectoryEntry sourceDir, DirectoryEntry destDir) throws IOException {
        for (org.apache.poi.poifs.filesystem.Entry entry : sourceDir) {
            if (entry instanceof DirectoryEntry) {
                // Need to recurse
                DirectoryEntry newDir = destDir.createDirectory(entry.getName());
                copy((DirectoryEntry) entry, newDir);
            } else {
                // Copy entry
                try (InputStream contents = new DocumentInputStream((DocumentEntry) entry)) {
                    destDir.createDocument(entry.getName(), contents);
                }
            }
        }
    }

}
