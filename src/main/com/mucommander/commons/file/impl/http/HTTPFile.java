package com.mucommander.commons.file.impl.http;

import com.mucommander.commons.file.*;
import com.mucommander.commons.io.BlockRandomInputStream;
import com.mucommander.commons.io.RandomAccessInputStream;
import com.mucommander.commons.io.RandomAccessOutputStream;
import com.mucommander.commons.io.base64.Base64Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * HTTPFile provides access to files located on an HTTP/HTTPS server.
 *
 * <p>The associated {@link FileURL} schemes are {@link FileProtocols#HTTP} and {@link FileProtocols#HTTPS}.
 * The host part of the URL designates the HTTP server. Credentials can be specified in the login and password parts
 * and will be used for HTTP Basic Authentication.
 *
 * <p>Here are a few examples of valid HTTP URLs:
 * <code>
 * http://www.mucommander.com/index.html<br>
 * http://www.mucommander.com/index.php?<br>
 * http://john:p4sswd@www.mucommander.com/restricted_area/<br>
 * </code>
 *
 * <p>
 * A notable feature of HTTPFile is that it handles HTML/XHTML files as archives: when any of the {@link #ls()} methods
 * is called, the HTML file is parsed and any link found in the code is considered as a file:
 * <ul>
 *  <li>If the link looks like a link to an HTML file, the child HTTPFile will be 'browsable' ({@link #isBrowsable()}
 * will return <code>true</code>).
 *  <li>If not, the file will just be a regular file.
 * </ul>
 *
 * <p>In order to avoid the cost of having to perform a HEAD request for each file, some guessing based on the URL and
 * its filename is performed to determine if the file is an HTML/XHTML file or not.
 * In practice, this works quite well for most sites but the algorithm will be confused by some non-conventional
 * file naming, for instance if an HTML file ends with the '.gif' extension.
 * <p>
 * A HEAD request is then issued only for non-HTML files, to determine their size and last modified date.
 * HTML files will thus have a size returned by {@link #getSize()} of <code>-1</code> (undetermined), and a date
 * returned by {@link #getLastModifiedDate()} corresponding to 'now' (current time).
 *
 * <p>Access to HTTP files is provided by the <code>java.net</code> API. The {@link #getUnderlyingFileObject()} method
 * allows to retrieve a <code>java.net.URL</code> instance corresponding to this HTTPFile.
 *
 * @author Maxence Bernard
 */
public class HTTPFile extends ProtocolFile {
    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPFile.class);

    /** java.net.URL corresponding to this */
    private URL url;

    /** Contains the attributes of the remote HTTP resource. Contains default values until the file has been resolved */
    private SimpleFileAttributes attributes;

    /** True if the file should be resolved on the remote HTTP server to fetch attribute values, false if these are
     * guessed. */
    private boolean resolve;

    /** True if file has been resolved on the remote HTTP server, either successfully or unsuccessfully */
    private boolean fileResolved;

    private boolean parentValSet;
    protected AbstractFile parent;
	
    /** Permissions for HTTP files: r-- (400 octal). Only the 'user' permissions bits are supported. */
    private final static FilePermissions PERMISSIONS = new SimpleFilePermissions(256, 448);

    /** User agent used for all HTTP connections made by HTTPFile */
    // TODO: add file API version, like trolCommander-file-API/1.0
    private static final String USER_AGENT = "trolCommander-file-API (Java "+System.getProperty("java.vm.version")
                                            + "; " + System.getProperty("os.name") + " " +
                                            System.getProperty("os.version") + " " + System.getProperty("os.arch") + ")";

    /** Matches HTML and XHTML attribute key/value pairs, where the value is surrounded by Single Quotes */
    private final static Pattern linkAttributePatternSQ = Pattern.compile("(src|href|SRC|HREF)='.*?'");

    /** Matches HTML and XHTML attribute key/value pairs, where the value is surrounded by Double Quotes */
    private final static Pattern linkAttributePatternDQ = Pattern.compile("(src|href|SRC|HREF)=\".*?\"");


    HTTPFile(FileURL fileURL) throws IOException {
        // TODO: optimize this
        this(fileURL, new URL(fileURL.toString(false)));
    }

	
    HTTPFile(FileURL fileURL, URL url) throws IOException {
        super(fileURL);

        String scheme = fileURL.getScheme().toLowerCase();
        if ((!scheme.equals(FileProtocols.HTTP) && !scheme.equals(FileProtocols.HTTPS)) || fileURL.getHost() == null) {
            throw new IOException();
        }

        this.url = url;

        attributes = getDefaultAttributes();

        String mimeType;
        String filename = fileURL.getFilename();
        // Simple/fuzzy heuristic to avoid file resolution (HEAD) in cases where we have good reasons to believe that
        // the URL denotes a HTML/XTHML document:
        //  - URL's path has no filename (e.g. http://www.mucommander.com/) or path ends with '/' (e.g. http://www.mucommander.com/download/)
        //  - URL has a query part (works most of the time, must not always)
        //  - URL has an extension that registered with an HTML/XHTML mime type
        if ((filename == null || fileURL.getPath().endsWith("/") || fileURL.getQuery()!=null || ((mimeType=MimeTypes.getMimeType(this))!=null && isParsableMimeType(mimeType)))) {
            attributes.setDirectory(true);
            resolve = false;
        } else {
            resolve = true;
        }
    }


    private static SimpleFileAttributes getDefaultAttributes() {
        SimpleFileAttributes attributes = new SimpleFileAttributes();
        attributes.setDate(System.currentTimeMillis());
        attributes.setSize(-1); // Unknown
        attributes.setPermissions(PERMISSIONS);
        // exist = false
        // isDirectory = false
        // path = null (unused)

        return attributes;
    }


    /**
     * Returns <code>true</code> if the given mime type corresponds to HTML or XHTML and can be parsed.
     *
     * @param mimeType a MIME type / content type
     * @return <code>true</code> if the given mime type corresponds to HTML or XHTML and can be parsed
     */
    private boolean isParsableMimeType(String mimeType) {
        return mimeType!=null
           && (mimeType.startsWith("text/html") || mimeType.startsWith("application/xhtml+xml") || mimeType.startsWith("application/xml"));
    }


    /**
     * Performs a HEAD request on the HTTP server to retrieve the file's attributes.
     *
     * @throws IOException if the HEAD request failed, either because the resource doesn't exist (404) or for any other
     * reason
     */
    private void resolveFile() throws IOException {
        try {
            LOGGER.info("Resolving {}", url);

            // Get URLConnection instance
            HttpURLConnection conn = getHttpURLConnection(url);

            // Use HEAD instead of GET as we don't need the body
            conn.setRequestMethod("HEAD");

            // Establish connection
            conn.connect();

            // Check HTTP response code and throw appropriate IOException if request failed
            checkHTTPResponse(conn);

            // Resolve date: use last-modified header, if not set use date header, and if still not set use System.currentTimeMillis
            long date = conn.getLastModified();
            if (date == 0) {
                date = conn.getDate();
                if (date == 0) {
                    date = System.currentTimeMillis();
                }
            }
            attributes.setDate(date);

            // Resolve size with content-length header (-1 if not available)
            attributes.setSize(conn.getContentLength());

            // Test if content is HTML
            String contentType = conn.getContentType();
            if (isParsableMimeType(contentType)) {
                attributes.setDirectory(true);
            }

            // File was successfully resolved on the remote HTTP server and thus exists
            attributes.setExists(true);
        } catch(IOException e) {
            LOGGER.info("Failed to resolve file {}", url, e);
        } finally {
            // Mark the file as resolved, even if the request failed
            fileResolved = true;
        }
    }


    /**
     * Opens and returns a <code>HttpURLConnection</code> to the resource denoted by the specified URL.
     * If the {@link FileURL} contained by this HTTPFile contains {@link Credentials}, these will be used as credentials
     * for <i>HTTP Basic Authentication<i>.
     *
     * @param url the URL to open
     * @return a HttpURLConnection to the resource denoted by the specified URL
     * @throws IOException if the HttpURLConnection could not be opened
     */
    private HttpURLConnection getHttpURLConnection(URL url) throws IOException {
        // Get URLConnection instance
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        // If credentials are contained in this HTTPFile's FileURL, use them for Basic HTTP Authentication
        Credentials credentials = fileURL.getCredentials();
        if (credentials != null)
            conn.setRequestProperty(
                "Authorization",
                "Basic "+ Base64Encoder.encode(credentials.getLogin()+":"+credentials.getPassword())
            );

        // Set user-agent header.
        conn.setRequestProperty("User-Agent", USER_AGENT);

        return conn;
    }


    /**
     * Checks the response code of the given HttpURLConnection and :
     * <ul>
     *  <li>throws an {@link AuthException} if the response code is 401 (Unauthorized)
     *  <li>throws an IOException if the response code is not in the 2xx - 3xx range (not a positive response)
     *  <li>does nothing otherwise
     *
     * @param conn the HttpURLConnection connection to examine
     * @throws AuthException if the response code is 401 (Unauthorized)
     * @throws IOException if the response code is not in the 2xx - 3xx range (not a positive response)
     */
    private void checkHTTPResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        LOGGER.info("response code = {}", responseCode);

        // If we got a 401 (Unauthorized) response, throw an AuthException to ask for credentials
        if (responseCode == 401) {
            throw new AuthException(fileURL, conn.getResponseMessage());
        }

        if (responseCode < 200 || responseCode >= 400) {
            throw new IOException(conn.getResponseMessage());
        }
    }

    private void checkResolveFile() {
        if (resolve && !fileResolved) {
            try {
                resolveFile();
            } catch(IOException e) {
                LOGGER.info("Failed to resolve {}", url, e);
                // file will be considered as resolved
            }
        }
    }

	
    /////////////////////////////////////////
    // AbstractFile methods implementation //
    /////////////////////////////////////////
	
    @Override
    public long getLastModifiedDate() {
        checkResolveFile();

        return attributes.getLastModifiedDate();
    }

    /**
     * Implementation notes: always throws {@link UnsupportedFileOperationException}.
     *
     * @throws UnsupportedFileOperationException always.
     */
    @Override
    @UnsupportedFileOperation
    public void setLastModifiedDate(long date) throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.CHANGE_DATE);
    }
	
    @Override
    public long getSize() {
        checkResolveFile();

        return attributes.getSize();	// Size == -1 if not known
    }
	
    @Override
    public AbstractFile getParent() {
        if (!parentValSet) {
            FileURL parentURL = fileURL.getParent();
            this.parent = parentURL == null ? null : FileFactory.getFile(parentURL);
            this.parentValSet = true;
        }
		
        return this.parent;
    }
	

    @Override
    public void setParent(AbstractFile parent) {
        this.parent = parent;
        this.parentValSet = true;
    }

    @Override
    public boolean exists() {
        if (!fileResolved) {
            // Note: file will only be resolved once, even if the request failed
            try {
                resolveFile();
            } catch(IOException ignore) {}
        }

        return attributes.exists();
    }

    @Override
    public FilePermissions getPermissions() {
        return attributes.getPermissions();
    }

    @Override
    public PermissionBits getChangeablePermissions() {
        return PermissionBits.EMPTY_PERMISSION_BITS;
    }

    @Override
    @UnsupportedFileOperation
    public void changePermission(int access, int permission, boolean enabled) throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.CHANGE_PERMISSION);
    }

    @Override
    public String getOwner() {
        return null;
    }

    @Override
    public boolean canGetOwner() {
        return false;
    }

    @Override
    public String getGroup() {
        return null;
    }

    @Override
    public boolean canGetGroup() {
        return false;
    }

    @Override
    @UnsupportedFileOperation
    public short getReplication() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.GET_REPLICATION);
    }

    @Override
    @UnsupportedFileOperation
    public long getBlocksize() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.GET_BLOCKSIZE);
    }

    @Override
    @UnsupportedFileOperation
    public void changeReplication(short replication) throws IOException {
        throw new UnsupportedFileOperationException(FileOperation.CHANGE_REPLICATION);
    }

    @Override
    public boolean isDirectory() {
        checkResolveFile();

        return attributes.isDirectory();
    }
	
    @Override
    public boolean isSymlink() {
        return false;
    }

    @Override
    public boolean isSystem() {
        return false;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        HttpURLConnection conn = getHttpURLConnection(this.url);

        // Establish connection
        conn.connect();

        // Check HTTP response code and throw appropriate IOException if request failed
        checkHTTPResponse(conn);

        return conn.getInputStream();
    }

    /**
     * Always throws an {@link UnsupportedFileOperationException}: HTTP files are read-only.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public OutputStream getOutputStream() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.WRITE_FILE);
    }

    /**
     * Always throws an {@link UnsupportedFileOperationException}: HTTP files are read-only.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public OutputStream getAppendOutputStream() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.APPEND_FILE);
    }

    @Override
    public RandomAccessInputStream getRandomAccessInputStream() throws IOException {
        return new HTTPRandomAccessInputStream();
    }

    /**
     * Always throws an {@link UnsupportedFileOperationException}: HTTP files are read-only.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public RandomAccessOutputStream getRandomAccessOutputStream() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.RANDOM_WRITE_FILE);
    }

    /**
     * Always throws an {@link UnsupportedFileOperationException}: HTTP files are read-only.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public void delete() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.DELETE);
    }

    /**
     * Always throws {@link UnsupportedFileOperationException} when called.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public void copyRemotelyTo(AbstractFile destFile) throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.COPY_REMOTELY);
    }

    /**
     * Always throws an {@link UnsupportedFileOperationException}: HTTP files are read-only.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public void renameTo(AbstractFile destFile) throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.RENAME);
    }

    /**
     * Always throws an {@link UnsupportedFileOperationException}: HTTP files are read-only.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public void mkdir() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.CREATE_DIRECTORY);
    }

    /**
     * Always throws {@link UnsupportedFileOperationException} when called.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public long getFreeSpace() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.GET_FREE_SPACE);
    }

    /**
     * Always throws {@link UnsupportedFileOperationException} when called.
     *
     * @throws UnsupportedFileOperationException always
     */
    @Override
    @UnsupportedFileOperation
    public long getTotalSpace() throws UnsupportedFileOperationException {
        throw new UnsupportedFileOperationException(FileOperation.GET_TOTAL_SPACE);
    }

    /**
     * Returns a <code>java.net.URL</code> instance corresponding to this file.
     */
    @Override
    public Object getUnderlyingFileObject() {
        return url;
    }

    @Override
    public AbstractFile[] ls() throws IOException {
        // Implementation note: javax.swing.text.html.HTMLEditorKit isn't quite powerful enough to be used
        try {
            HttpURLConnection conn = resolveAndConnect(this.url);
            URL contextURL = conn.getURL();

            try (BufferedReader br = createReader(conn)) {
                return findChildren(br, contextURL);
            }
        } catch (Exception e) {
            LOGGER.info("Exception caught while parsing HTML, throwing IOException", e);

            if (e instanceof IOException) {
                throw (IOException) e;
            }

            throw new IOException();
        }
    }

    private HttpURLConnection resolveAndConnect(URL url) throws IOException {
        final int maxRedirects = 10;
        for (int i = 0; i < maxRedirects; i++) {
            // Get a connection instance
            HttpURLConnection conn = getHttpURLConnection(url);

            // Disable automatic redirections to track URL change
            conn.setInstanceFollowRedirects(false);

            // Establish connection
            conn.connect();

            // Check HTTP response code and throw appropriate IOException if request failed
            checkHTTPResponse(conn);

            int responseCode = conn.getResponseCode();

            // Test if response code is in the 3xx range (redirection) and if 'Location' field is set

            if (responseCode >= 300 && responseCode < 400) {
                String locationHeader = conn.getHeaderField("Location");
                if (locationHeader != null) {
                    // Redirect to Location field and remember context url
                    LOGGER.info("Location header = {}", locationHeader);
                    url = new URL(url, locationHeader);
                    // One more time
                    continue;
                }
            }
            return conn;
        }
        throw new IOException("too many redirects");
    }

    private AbstractFile[] findChildren(BufferedReader br, URL contextURL) throws IOException {
        List<AbstractFile> children = new ArrayList<>();
        // List that contains children URL, a TreeSet for fast (log(n)) search operations
        Set<String> childrenURL = new TreeSet<>();
        Credentials credentials = fileURL.getCredentials();

//            String parentPath = fileURL.getPath();
//            if (!parentPath.endsWith("/")) {
//                parentPath += "/";
//            }

        String parentHost = fileURL.getHost();

        //FileURL tempChildURL = (FileURL)fileURL.clone();

        Pattern pattern;
        String line;
        final String parentPath = contextURL.toString();
        while ((line = br.readLine()) != null) {
            for (pattern = linkAttributePatternSQ; ; pattern = linkAttributePatternDQ) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    String match = matcher.group();
                    String link = match.substring(match.indexOf(pattern==linkAttributePatternSQ ? '\'' : '\"') + 1, match.length()-1);

                    // These are not proper URLs, skip them
                    // Don't add the same link more than once
                    if (!linkCanBeDownloaded(link) || childrenURL.contains(link)) {
                        continue;
                    }

                    try {
                        LOGGER.trace("creating child {} context={}", link, contextURL);
                        URL childURL = new URL(contextURL, link);

                        // create the child FileURL instance
                        FileURL childFileURL = FileURL.getFileURL(childURL.toExternalForm());
                        // Keep the parent's credentials (HTTP basic authentication), only if the host is the same.
                        // It would otherwise be unsafe.
                        if (parentHost.equals(childFileURL.getHost())) {
                            childFileURL.setCredentials(credentials);
                        }

                        // TODO: resolve file here instead of in the constructor, and multiplex requests just like a browser

                        // skip parent
                        if (!childURL.toString().contains(parentPath)) {
                            continue;
                        }
                        children.add(FileFactory.getFile(childFileURL, null, childURL, childURL.toString()));
                        childrenURL.add(link);
                    } catch (IOException e) {
                        LOGGER.info("Cannot create child: {}", e);
                    }
                }

                if (pattern == linkAttributePatternDQ) {
                    break;
                }
            }
        }

        AbstractFile childrenArray[] = new AbstractFile[children.size()];
        children.toArray(childrenArray);
        return childrenArray;
    }


    private boolean linkCanBeDownloaded(String link) {
        link = link.toLowerCase();
        return !(link.startsWith("mailto") || link.startsWith("#") || link.startsWith("javascript:"));
    }

    private String getContentEncoding(HttpURLConnection conn) throws IOException {
        // Retrieve content type and throw an IOException if doesn't correspond to a parsable type (HTML/XHTML)
        String contentType = conn.getContentType();
        if (contentType == null || !isParsableMimeType(contentType)) {
            throw new IOException("Document cannot be parsed (not HTML or XHTML)");  // Todo: localize this message
        }
        int pos;
        String enc = null;
        // Extract content type information (if any)
        if ((pos = contentType.indexOf("charset")) >= 0 || (pos = contentType.indexOf("Charset")) >= 0) {
            StringTokenizer st = new StringTokenizer(contentType.substring(pos));
            enc = st.nextToken();
        }
        return enc;
    }

    private BufferedReader createReader(HttpURLConnection conn) throws IOException {
        // Use the encoding reported in HTTP header if there was one, otherwise just use the default encoding
        String enc = getContentEncoding(conn);

        InputStream in = conn.getInputStream();
        InputStreamReader ir;
        if (enc == null) {
            ir = new InputStreamReader(in);
        } else {
            try {
                ir = new InputStreamReader(in, enc);
            } catch (UnsupportedEncodingException e) {
                ir = new InputStreamReader(in);
            }
        }

        return new BufferedReader(ir);
    }


    ////////////////////////
    // Overridden methods //
    ////////////////////////

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public String getName() {
        try {
            return java.net.URLDecoder.decode(super.getName(), "utf-8");
        } catch(Exception e) {
            return super.getName();
        }
    }

    /**
     * Overrides AbstractFile's getInputStream(long) method to provide a more efficient implementation:
     * use the HTTP 1.1 header to start the transfer at the given offset.
     */
    @Override
    public InputStream getInputStream(long offset) throws IOException {
        HttpURLConnection conn = getHttpURLConnection(this.url);

        // Set header that allows to resume transfer
        conn.setRequestProperty("Range", "bytes="+offset+"-");

        // Establish connection
        conn.connect();

        // Check HTTP response code and throw appropriate IOException if request failed
        checkHTTPResponse(conn);

        return conn.getInputStream();
    }


    ///////////////////
    // Inner classes //
    ///////////////////


    /**
     * HTTPRandomAccessInputStream extends BlockRandomInputStream to provide random read access to an HTTPFile.
     * It uses the 'Range' request header to read the HTTP resource partially, chunk by chunk and reposition the offset
     * when {@link #seek(long)} is called.
     */
    private class HTTPRandomAccessInputStream extends BlockRandomInputStream {

        /** Amount of data returned  */
        private final static int CHUNK_SIZE = 1024;

        /** Length of the HTTP resource */
        private long length;


        private HTTPRandomAccessInputStream() throws IOException {
            super(CHUNK_SIZE);

            // HEAD the HTTP resource to get its length
            if (!fileResolved) {
                resolveFile();
            }

            length = getSize();
            if (length == -1) {        // Knowing the content length is required
                throw new IOException();
            }
        }

        ///////////////////////////////////////////
        // BlockRandomInputStream implementation //
        ///////////////////////////////////////////

        @Override
        protected int readBlock(long fileOffset, byte block[], int blockLen) throws IOException {
            HttpURLConnection conn = getHttpURLConnection(url);

            // Note: 'Range' may not be supported by the HTTP server, in that case an IOException will be thrown
            conn.setRequestProperty("Range", "bytes="+fileOffset +"-"+ Math.min(fileOffset+blockLen, length-1));

            conn.connect();
            checkHTTPResponse(conn);

            // Read up to blockLen bytes
            try (InputStream in = conn.getInputStream()) {
                int totalRead = 0;
                int read;
                while (totalRead < blockLen) {
                    read = in.read(block, totalRead, blockLen - totalRead);
                    if (read == -1) {
                        break;
                    }
                    totalRead += read;
                }

                return totalRead;
            }
        }

        public long getLength() {
            return length;
        }

        @Override
        public void close() throws IOException {
            // No-op, the underlying stream is already closed
        }
    }
}
