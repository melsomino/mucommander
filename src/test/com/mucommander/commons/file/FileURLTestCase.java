package com.mucommander.commons.file;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.mucommander.commons.runtime.OsFamily;

import java.net.MalformedURLException;

/**
 * A generic test case for {@link com.mucommander.commons.file.FileURL}. This class is abstract and must be extended for
 * each URL scheme to be tested. Those tests should be completed by additional scheme-specific tests for
 * certain particularities that would not covered by this generic test case.  
 *
 * @see com.mucommander.commons.file.FileURL
 * @author Maxence Bernard
 */
public abstract class FileURLTestCase {

    ////////////////////
    // Helper methods //
    ////////////////////

    /**
     * Returns the scheme-specific version of the path.
     *
     * @param path a forward slash-separated path
     * @return the corresponding scheme-specific path
     */
    protected String getSchemePath(String path) {
        String separator = getPathSeparator();

        if(!separator.equals("/"))
            path = "/" + path.replace("/", separator);

        return path;
    }

    /**
     * Creates a URL from the given parts, ensuring that parsing it yields the given parts, and returns it.
     *
     * @param login login part, may be <code>null</code>
     * @param password password part, may be <code>null</code>
     * @param host host part, may be <code>null</code>
     * @param port port part, <code>-1</code> for none
     * @param path path part
     * @param query query part, may be <code>null</code>
     * @return a URL corresponding to the given parts
     * @throws MalformedURLException if the URL cannot be parsed
     */
    protected FileURL getURL(String login, String password, String host, int port, String path, String query) throws MalformedURLException {
        String scheme = getScheme();
        StringBuilder sb = new StringBuilder(scheme+"://");

        if(host!=null) {
            if(login!=null) {
                sb.append(login);

                if(password!=null) {
                    sb.append(':');
                    sb.append(password);
                }

                sb.append('@');
            }

            sb.append(host);

            if(port!=-1) {
                sb.append(':');
                sb.append(port);
            }
        }

        path = getSchemePath(path);
        sb.append(path);

        if(query!=null) {
            sb.append('?');
            sb.append(query);
        }

        // Assert that each of the url's parts match

        FileURL url = FileURL.getFileURL(sb.toString());

        Assert.assertEquals(scheme,url.getScheme());

        if(host!=null) {
            if(login!=null) {
                Assert.assertEquals(login,url.getLogin());
                assert url.containsCredentials();

                if(password!=null)
                    Assert.assertEquals(password,url.getPassword());

                Assert.assertTrue(new Credentials(login, password).equals(url.getCredentials(), true));
            }

            Assert.assertEquals(host,url.getHost());
            assert port == url.getPort();
        }

        if(query!=null && !isQueryParsed()) {
            assert url.getQuery() == null;
            path = path+"?"+query;
        }
        else if(query == null)
            assert url.getQuery() == null;
        else
            Assert.assertEquals(query,url.getQuery());

        assertPathEquals(path, url);

        // Test the URL's string representation
        Assert.assertTrue(url.equals(FileURL.getFileURL(url.toString(true, false))));
        Assert.assertTrue(url.equals(FileURL.getFileURL(url.toString(false, false)), false, false));

        return url;
    }

    /**
     * Shorthand for {@link #getURL(String, String, String, int, String, String)} called with just a path.
     *
     * @param path the path part
     * @return a URL corresponding to the given part
     * @throws MalformedURLException if the URL cannot be parsed
     */
    protected FileURL getURL(String path) throws MalformedURLException {
        return getURL(null, null, null, -1, path, null);
    }

    /**
     * Shorthand for {@link #getURL(String, String, String, int, String, String)} called with just a host and path.
     *
     * @param host the host part, may be <code>null</code>  
     * @param path the path part
     * @return a URL corresponding to the given part
     * @throws MalformedURLException if the URL cannot be parsed
     */
    protected FileURL getURL(String host, String path) throws MalformedURLException {
        return getURL(null, null, host, -1, path, null);
    }


    /**
     * Returns the simplest FileURL instance possible using the scheme returned by {@link #getScheme()}, containing
     * only the scheme and '/' as a path. For instance, if <code>http</code> is the current scheme, a FileURL with
     * <code>http:///</code> as a string representation will be returned.
     *
     * @return a 'root' FileURL instance with the scheme returned by {@link # getScheme ()}
     * @throws MalformedURLException should never happen
     */
    protected FileURL getRootURL() throws MalformedURLException {
        return FileURL.getFileURL(getScheme()+":///");
    }

    /**
     * Attemps to parse the specified url using {@link FileURL#getFileURL(String)} and returns <code>true</code> if it
     * succeeded, <code>false</code> if it threw a <code>MalformedURLException</code>.
     *
     * @param url the URL to try and parse
     * @return <code>true</code> if the URL could be parsed, <code>false</code> if a <code>MalformedURLException</code> was thrown
     */
    protected boolean canParse(String url) {
        try {
            FileURL.getFileURL(url);
            return true;
        }
        catch(MalformedURLException e) {
            return false;
        }
    }

    /**
     * Asserts that both URLs are equal, and that their hashcodes are the same.
     *
     * @param url1 first url to test
     * @param url2 second url to test
     */
    protected void assertEquals(FileURL url1, FileURL url2) {
        Assert.assertEquals(url1,url2);
        Assert.assertEquals(url2,url1);
        assert url1.hashCode()==url2.hashCode();
    }

    /**
     * Asserts that both URLs are equal, comparing credentials and properties as requested. If both the
     * <code>compareCredentials</code> and <code>compareProperties</code> parameters are <code>true</code>, this method
     * asserts that the hashcode of both URLs are the same.
     *
     * @param url1 first url to test
     * @param url2 second url to test
     * @param compareCredentials if <code>true</code>, the login and password parts of both FileURL need to be
     * equal (case-sensitive) for the FileURL instances to be equal
     * @param compareProperties if <code>true</code>, all properties need to be equal (case-sensitive) in both
     * FileURL for them to be equal
     */
    protected void assertEquals(FileURL url1, FileURL url2, boolean compareCredentials, boolean compareProperties) {
        Assert.assertTrue(url1.equals(url2, compareCredentials, compareProperties));
        Assert.assertTrue(url2.equals(url1, compareCredentials, compareProperties));

        // Compare hash codes only if both flags are true.
        assert !compareCredentials || !compareProperties || url1.hashCode() == url2.hashCode();
    }

    /**
     * Asserts that both URLs are not equal.
     *
     * @param url1 first url to test
     * @param url2 second url to test
     */
    protected void assertNotEquals(FileURL url1, FileURL url2) {
        Assert.assertFalse(url1.equals(url2));
        Assert.assertFalse(url2.equals(url1));
    }

    /**
     * Asserts that both URLs are not equal, comparing credentials and properties as requested.
     *
     * @param url1 first url to test
     * @param url2 second url to test
     * @param compareCredentials if <code>true</code>, the login and password parts of both FileURL need to be
     * equal (case-sensitive) for the FileURL instances to be equal
     * @param compareProperties if <code>true</code>, all properties need to be equal (case-sensitive) in both
     * FileURL for them to be equal
     */
    protected void assertNotEquals(FileURL url1, FileURL url2, boolean compareCredentials, boolean compareProperties) {
        Assert.assertTrue(!url1.equals(url2, compareCredentials, compareProperties));
        Assert.assertTrue(!url2.equals(url1, compareCredentials, compareProperties));
    }

    /**
     * Asserts that the path of the given URL is equal to the given expected path.
     *
     * @param expectedPath the expected path
     * @param url URL to test
     */
    protected void assertPathEquals(String expectedPath, FileURL url) {
        Assert.assertEquals(expectedPath,url.getPath());
    }



    //////////////////
    // Test methods //
    //////////////////

    /**
     * Ensures that the values returned by {@link FileURL#getStandardPort()} and {@link SchemeHandler#getStandardPort()}
     * match the expected one returned by {@link #getDefaultPort()}.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testDefaultPort() throws MalformedURLException {
        FileURL url = getRootURL();
        int expectedDefaultPort = getDefaultPort();

        // Assert that the default port value returned by the FileURL and its handler match the expected one
        // and are consistent
        assert expectedDefaultPort == url.getStandardPort();
        assert expectedDefaultPort == url.getHandler().getStandardPort();

        // Assert that the default port value is valid: either -1 or comprised between 1 and 65535
        assert expectedDefaultPort==-1||(expectedDefaultPort>0 && expectedDefaultPort<65536);
    }


    /**
     * Ensures that the values returned by {@link FileURL#getGuestCredentials()} and {@link SchemeHandler#getGuestCredentials()}
     * match the expected one returned by {@link #getGuestCredentials()}.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testGuestCredentials() throws MalformedURLException {
        FileURL url = getRootURL();
        Credentials expectedGuestCredentials = getGuestCredentials();

        // Assert that the guest credentials values returned by the FileURL and its handler match the expected one
        // and are consistent
        if(expectedGuestCredentials == null) {
            assert url.getGuestCredentials() == null;
            assert url.getHandler().getGuestCredentials() == null;
        }
        else {
            Assert.assertEquals(expectedGuestCredentials,url.getGuestCredentials());
            Assert.assertEquals(expectedGuestCredentials,url.getHandler().getGuestCredentials());
        }
    }

    /**
     * Ensures that the values returned by {@link FileURL#getAuthenticationType()} ()} and {@link SchemeHandler#getAuthenticationType()}
     * match the expected value returned by {@link #getAuthenticationType()}, and that the value is one of the constants
     * defined in {@link AuthenticationType}.
     * If the authentication type is {@link AuthenticationType#NO_AUTHENTICATION}, verifies that
     * {@link #getGuestCredentials()} returns <code>null</code>.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testAuthenticationType() throws MalformedURLException {
        FileURL url = getRootURL();
        AuthenticationType expectedAuthenticationType = getAuthenticationType();

        Assert.assertEquals(expectedAuthenticationType,url.getAuthenticationType());
        Assert.assertEquals(expectedAuthenticationType,url.getHandler().getAuthenticationType());

        assert expectedAuthenticationType==AuthenticationType.NO_AUTHENTICATION
                || expectedAuthenticationType==AuthenticationType.AUTHENTICATION_REQUIRED
                || expectedAuthenticationType==AuthenticationType.AUTHENTICATION_OPTIONAL;

        assert expectedAuthenticationType != AuthenticationType.NO_AUTHENTICATION || url.getGuestCredentials() == null;
     }

    /**
     * Ensures that the values returned by {@link FileURL#getPathSeparator()} and {@link SchemeHandler#getPathSeparator()}
     * match the expected one returned by {@link #getPathSeparator()}.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testPathSeparator() throws MalformedURLException {
        FileURL url = getRootURL();
        String expectedPathSeparator = url.getPathSeparator();

        // Assert that the path separator values returned by the FileURL and its handler match the expected one
        // and are consistent
        Assert.assertEquals(expectedPathSeparator,url.getPathSeparator());
        Assert.assertEquals(expectedPathSeparator,url.getHandler().getPathSeparator());
    }


    /**
     * Tests {@link com.mucommander.commons.file.FileURL#getRealm()} by ensuring that it returns the same URL only with the
     * path stripped out.
     * <p>
     * <b>Important:</b> this method must be overridden for protocols that have a specific realm notion (like SMB) or
     * else the test will fail.
     *
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testRealm() throws MalformedURLException {
        assertEquals(getURL("host", "/"), getURL("host", "/path/to/file").getRealm());
    }


    /**
     * Ensures that the query part is parsed only if it should be, as specified by {@link #isQueryParsed()}.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testQueryParsing() throws MalformedURLException {
        FileURL url = getURL(null, null, "host", -1, "/path", "query&param=value");
        String query = url.getQuery();

        if(isQueryParsed()) {
            Assert.assertEquals("query&param=value",query);
        }
        else {
            assert query == null;
        }
    }


    /**
     * Ensures that FileURL#getParent() works as expected.
     * 
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testParent() throws MalformedURLException {
        FileURL url = getURL("login", "password", "host", 10000, "/path/to", "query&param=value");
        url.setProperty("key", "value");

        FileURL parentURL = url.getParent();

        // Test path and filename
        assertPathEquals(getSchemePath("/path/"), parentURL);
        Assert.assertEquals("path",parentURL.getFilename());

        // Assert that schemes, hosts and ports match
        Assert.assertEquals(url.getScheme(),parentURL.getScheme());
        Assert.assertEquals(url.getHost(),parentURL.getHost());
        assert url.getPort() == parentURL.getPort();

        // Assert that credentials match
        Assert.assertEquals(url.getCredentials(),parentURL.getCredentials());
        Assert.assertEquals(url.getLogin(),parentURL.getLogin());
        Assert.assertEquals(url.getPassword(),parentURL.getPassword());

        // Assert that the sample property is in the parent URL
        Assert.assertEquals("value",parentURL.getProperty("key"));

        // Assert that handlers match
        Assert.assertEquals(url.getHandler(),parentURL.getHandler());

        // Assert that the query part is null
        assert parentURL.getQuery() == null;

        // One more time, the parent path is now "/"

        url = parentURL;
        parentURL = url.getParent();

        // Test path and filename
        assertPathEquals(getSchemePath("/"), parentURL);
        assert parentURL.getFilename() == null;

        // The parent URL should now be null   

        // Test path and filename
        url = parentURL;
        assert url.getParent() == null;
    }


    /**
     * Parses URLs, some borderline but that we consider nonetheless valid, and ensures that they parse without error
     * and that getters return proper part values.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testParsing() throws MalformedURLException {
        // Test a sample URL with all parts used
        getURL("login", "password", "host", 10000, "/path/to", "query");

        // Ensure that login and password parts can contain '@' characters without disrupting the parsing
        getURL("login@domain.com", "password@domain.com", "host", 10000, "/path/to", "query");

        // Ensure that paths can contain '@' characters without disrupting the parsing
        getURL("login", "password", "host", 10000, "/path@at/to@at", "query");

        // Ensure that empty port parts are tolerated
        getURL("login", "password", "host", -1, "/path@at/to@at", "query");
    }

    /**
     * Ensure that non URL-safe characters in login and password parts are properly handled, both when parsing
     * and representing URLs as string.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testCredentialsURLEncoding() throws MalformedURLException {
        FileURL url = getRootURL();

        String urlDecodedString = ":@&=+$,/?t%#[]";
        String urlEncodedString = "%3A%40%26%3D%2B%24%2C%2F%3Ft%25%23%5B%5D";

        url.setCredentials(new Credentials(urlDecodedString, urlDecodedString));
        String urlRep = url.getScheme()+"://"+urlEncodedString+":"+urlEncodedString+"@";
        Assert.assertEquals(urlRep,url.toString(true, false));

        url = FileURL.getFileURL(urlRep);
        Credentials credentials = url.getCredentials();
        Assert.assertEquals(credentials.getLogin(),urlDecodedString);
        Assert.assertEquals(credentials.getPassword(),urlDecodedString);
    }

    /**
     * Tests FileURL's getters and setters.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testAccessors() throws MalformedURLException {
        FileURL url = getRootURL();

        String scheme = getScheme();
        Credentials credentials = new Credentials("login", "password");
        String host = "host";
        int port = 10000;
        String path = getSchemePath("/path/to");
        String query = "query";

        url.setScheme(scheme);
        url.setCredentials(credentials);
        url.setHost(host);
        url.setPort(port);
        url.setPath(path);
        url.setQuery(query);
        url.setProperty("name", "value");

        Assert.assertEquals(scheme,url.getScheme());
        Assert.assertTrue(credentials.equals(url.getCredentials(), true));
        Assert.assertEquals(host,url.getHost());
        assert port == url.getPort();
        Assert.assertEquals(path,url.getPath());
        Assert.assertEquals(query,url.getQuery());
        Assert.assertEquals("to",url.getFilename());
        Assert.assertEquals("value",url.getProperty("name"));

        // Test null values

        url.setCredentials(null);
        url.setHost(null);
        url.setPort(-1);
        url.setPath("/");
        url.setQuery(null);
        url.setProperty("name", null);

        Assert.assertEquals(scheme,url.getScheme());
        assert url.getCredentials() == null;
        assert !url.containsCredentials();
        assert url.getHost() == null;
        assert -1 == url.getPort();
        Assert.assertEquals("/",url.getPath());
        assert url.getQuery() == null;
        assert url.getFilename() == null;
        assert url.getProperty("name") == null;
        Assert.assertEquals((scheme+"://"),url.toString(true, false));

        // Path cannot be null, the path is supposed to be "/" if a null or empty value is specified
        url.setPath(null);
        Assert.assertEquals("/",url.getPath());
        url.setPath("");
        Assert.assertEquals("/",url.getPath());

        // Path must always start with a leading '/', if the specified does not then a '/' is automatically added
        url.setPath("path/to");
        Assert.assertEquals("/path/to",url.getPath());
    }

    /**
     * Tests {@link FileURL#getFilename()} method.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testFilename() throws MalformedURLException {
        Assert.assertEquals("file",getURL("/path/to/file").getFilename());
        Assert.assertEquals("file",getURL("/path/to/file/").getFilename());
        Assert.assertEquals("path",getURL("/path").getFilename());
        Assert.assertEquals("path",getURL("/path/").getFilename());
        assert getRootURL().getFilename() == null : "/";

        Assert.assertEquals((isQueryParsed()?"file":"file?param=value"),getURL(null, null, null, -1, "/path/to/file", "param=value").getFilename());
    }

    /**
     * Tests FileURL's <code>toString</code> methods.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testStringRepresentation() throws MalformedURLException {
        FileURL url = getURL("login", "password", "host", 10000, "/path", "query");
        String path = getSchemePath("/path");
        String urlString = getScheme()+"://host:10000"+path+"?query";

        Assert.assertEquals(urlString,url.toString());
        Assert.assertEquals(urlString,url.toString(false));
        Assert.assertEquals(urlString,url.toString(false, false));

        urlString = getScheme()+"://login:password@host:10000"+path+"?query";
        Assert.assertEquals(urlString,url.toString(true));
        Assert.assertEquals(urlString,url.toString(true, false));

        urlString = getScheme()+"://login:********@host:10000"+path+"?query";
        Assert.assertEquals(urlString,url.toString(true, true));
    }


    /**
     * Tests <code>equals</code> methods.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testEquals() throws MalformedURLException {
        // No query part, as it is not parsed by all schemes
        FileURL url1 = getURL("login", "password", "host", 10000, "/path",  null);
        url1.setProperty("name", "value");
        FileURL url2 = (FileURL)url1.clone();

        // Assert that both URLs are equal
        assertEquals(url1, url2);
        assertEquals(url1, url2, true, true);

        // Add a trailing path separator to one of the URL's path and assert they are still equal
        url1.setPath(url1.getPath()+url1.getPathSeparator());
        assertEquals(url1, url2);
        assertEquals(url1, url2, true, true);

        // Assert that having the port part set to the standart port is equivalent to not having a port part (-1)
        url1.setPort(url1.getStandardPort());
        url2.setPort(-1);
        assertEquals(url1, url2);
        assertEquals(url1, url2, true, true);

        // Assert that the scheme comparison is case-insensitive
        url1.setScheme(url1.getScheme().toUpperCase());
        assertEquals(url1, url2);
        assertEquals(url1, url2, true, true);

        // Assert that the host comparison is case-insensitive
        url1.setHost(url1.getHost().toUpperCase());
        assertEquals(url1, url2);
        assertEquals(url1, url2, true, true);

        // Assert that the path comparison is case-sensitive
        if(OsFamily.getCurrent().isCaseSensitiveFilesystem()){
        url1.setPath(url1.getPath().toUpperCase());
        assertNotEquals(url1, url2);
        assertNotEquals(url1, url2, true, true);
        }

        // Make both URLs equal again
        url1.setPath(url2.getPath());
        assertEquals(url1, url2);
        assertEquals(url1, url2, true, true);

        // Assert that the query comparison is case-sensitive
        url1.setQuery("query");
        url2.setQuery("QUERY");
        assertNotEquals(url1, url2);
        assertNotEquals(url1, url2, true, true);

        // Make both URLs equal again
        url1.setQuery(url2.getQuery());
        assertEquals(url1, url2);
        assertEquals(url1, url2, true, true);

        // Assert that the credentials comparison is case-sensitive
        url1.setCredentials(new Credentials("LOGIN", "password"));
        assertEquals(url1, url2, false, false);
        assertNotEquals(url1, url2, true, true);
        url1.setCredentials(new Credentials("login", "PASSWORD"));
        assertEquals(url1, url2, false, false);
        assertNotEquals(url1, url2, true, true);

        // Assert that URLs are equal if credentials comparison is disabled
        assertEquals(url1, url2, false, true);

        // Make both URLs equal again
        url1.setCredentials(new Credentials("login", "password"));
        assertEquals(url1, url2, true, true);

        // Assert that the properties comparison is case-sensitive
        url1.setProperty("name", null);
        url1.setProperty("NAME", "value");
        assertEquals(url1, url2, false, false);
        assertNotEquals(url1, url2, true, true);
        url1.setProperty("name", "VALUE");
        assertEquals(url1, url2, false, false);
        assertNotEquals(url1, url2, true, true);

        // Assert that URLs are equal if properties comparison is disabled
        assertEquals(url1, url2, true, false);

        // Make both URLs equal again
        url1.setProperty("NAME", null);
        url1.setProperty("name", "value");
        assertEquals(url1, url2, true, true);

        // Assert that the properties comparison fails if an extra property is added to one of the URLs
        url1.setProperty("name2", "value2");
        assertNotEquals(url1, url2, true, true);

        // Assert that URLs are equal if properties comparison is disabled
        assertEquals(url1, url2, true, false);

        // Make both URLs equal again
        url1.setProperty("name2", null);
        assertEquals(url1, url2, true, true);
    }


    /**
     * Tests {@link FileURL#clone()}.
     *
     * @throws MalformedURLException should not happen
     */
    @Test
    public void testClone() throws MalformedURLException {
        FileURL url = getURL("login", "password", "host", 10000, "/path/to", "query");
        url.setProperty("name", "value");

        FileURL clonedURL = (FileURL)url.clone();

        // Assert that both instances are equal according to FileURL#equals
        assertEquals(url, clonedURL);

        // Assert that both URL's string representations (with credentials) are equal
        Assert.assertEquals(url.toString(true),clonedURL.toString(true));

        // Assert that both instances are not one and the same
        assert url!=clonedURL;

        // Assert that the property has survived the cloning
        Assert.assertEquals("value",clonedURL.getProperty("name"));
    }

    /**
     * Tests a few invalid URLs and makes sure {@link FileURL#getFileURL} throws a <code>MalformedURLException</code>.
     *
     */
    @Test
    public void testInvalidURLs() {
        // relative URLs
        assert !canParse("relative");
        assert !canParse("C:");
        assert !canParse("scheme:/");

        // Invalid port (non-numeric)
        assert !canParse(getScheme()+"://host:port/path");
    }
    

    //////////////////////
    // Abstract methods //
    //////////////////////

    protected abstract String getScheme();

    protected abstract int getDefaultPort();

    protected abstract AuthenticationType getAuthenticationType();

    protected abstract Credentials getGuestCredentials();

    protected abstract String getPathSeparator();
    
    protected abstract boolean isQueryParsed();
}
