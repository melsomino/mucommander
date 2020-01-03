package com.mucommander.commons.file.util;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileFactory;
import com.mucommander.commons.file.impl.TestFile;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;

/**
 * A test case for {@link FileComparator}.
 * @author Mariusz Jakubowski
 *
 */
public class FileComparatorTest {
  
    AbstractFile[] files;
    private TestFile A;
    private TestFile B;
    private TestFile C;
    private TestFile D;
    

    @BeforeMethod
    protected void setUp() throws Exception {
        A = new TestFile(FileFactory.getTemporaryFolder() + "A", false, 500, 1, null);
        B = new TestFile(FileFactory.getTemporaryFolder() + "B.e9.e1", true, 0, 2, null);
        C = new TestFile(FileFactory.getTemporaryFolder() + "C.e3", false, 200, 3, null);
        D = new TestFile(FileFactory.getTemporaryFolder() + "D.e2", true, 0, 4, null);
        files = new AbstractFile[4];
        files[0] = C;
        files[1] = D;
        files[2] = A;
        files[3] = B;
    }

    @Test
    public void testCompareNameDir() {
        Arrays.sort(files, new FileComparator(FileComparator.NAME_CRITERION, true, false,true));
        assert B.equals(files[0]);
        assert D.equals(files[1]);
        assert A.equals(files[2]);
        assert C.equals(files[3]);
    }

    @Test
    public void testCompareNameDirDesc() {
        Arrays.sort(files, new FileComparator(FileComparator.NAME_CRITERION, false, false,true));
        assert D.equals(files[0]);
        assert B.equals(files[1]);
        assert C.equals(files[2]);
        assert A.equals(files[3]);
    }

    @Test
    public void testCompareName() {
        Arrays.sort(files, new FileComparator(FileComparator.NAME_CRITERION, true, false,false));
        assert A.equals(files[0]);
        assert B.equals(files[1]);
        assert C.equals(files[2]);
        assert D.equals(files[3]);
    }

    @Test
    public void testCompareNameDesc() {
        Arrays.sort(files, new FileComparator(FileComparator.NAME_CRITERION, false, false,false));
        assert D.equals(files[0]);
        assert C.equals(files[1]);
        assert B.equals(files[2]);
        assert A.equals(files[3]);
    }

    @Test
    public void testCompareSizeDir() {
        Arrays.sort(files, new FileComparator(FileComparator.SIZE_CRITERION, true, false,true));
        assert B.equals(files[0]);
        assert D.equals(files[1]);
        assert C.equals(files[2]);
        assert A.equals(files[3]);
    }

    @Test
    public void testCompareSize() {
        Arrays.sort(files, new FileComparator(FileComparator.SIZE_CRITERION, true, false,false));
        assert B.equals(files[0]);
        assert D.equals(files[1]);
        assert C.equals(files[2]);
        assert A.equals(files[3]);
    }

    @Test
    public void testCompareSizeDirDesc() {
        Arrays.sort(files, new FileComparator(FileComparator.SIZE_CRITERION, false, false,true));
        assert D.equals(files[0]);
        assert B.equals(files[1]);
        assert A.equals(files[2]);
        assert C.equals(files[3]);
    }

    @Test
    public void testCompareSizeDesc() {
        Arrays.sort(files, new FileComparator(FileComparator.SIZE_CRITERION, false, false,false));
        assert A.equals(files[0]);
        assert C.equals(files[1]);
        assert D.equals(files[2]);
        assert B.equals(files[3]);
    }

    @Test
    public void testCompareDateDir() {
        Arrays.sort(files, new FileComparator(FileComparator.DATE_CRITERION, true, false,true));
        assert B.equals(files[0]);
        assert D.equals(files[1]);
        assert A.equals(files[2]);
        assert C.equals(files[3]);
    }

    @Test
    public void testCompareDate() {
        Arrays.sort(files, new FileComparator(FileComparator.DATE_CRITERION, true, false,false));
        assert A.equals(files[0]);
        assert B.equals(files[1]);
        assert C.equals(files[2]);
        assert D.equals(files[3]);
    }

    @Test
    public void testCompareDateDirDesc() {
        Arrays.sort(files, new FileComparator(FileComparator.DATE_CRITERION, false, false,true));
        assert D.equals(files[0]);
        assert B.equals(files[1]);
        assert C.equals(files[2]);
        assert A.equals(files[3]);
    }

    @Test
    public void testCompareDateDesc() {
        Arrays.sort(files, new FileComparator(FileComparator.DATE_CRITERION, false, false,false));
        assert D.equals(files[0]);
        assert C.equals(files[1]);
        assert B.equals(files[2]);
        assert A.equals(files[3]);
    }

    @Test
    public void testCompareExtDir() {
        Arrays.sort(files, new FileComparator(FileComparator.EXTENSION_CRITERION, true, false,true));
        assert B.equals(files[0]);
        assert D.equals(files[1]);
        assert A.equals(files[2]);
        assert C.equals(files[3]);
    }

    @Test
    public void testCompareExt() {
        Arrays.sort(files, new FileComparator(FileComparator.EXTENSION_CRITERION, true, false,false));
        assert A.equals(files[0]);
        assert B.equals(files[1]);
        assert D.equals(files[2]);
        assert C.equals(files[3]);
    }

    @Test
    public void testCompareExtDirDesc() {
        Arrays.sort(files, new FileComparator(FileComparator.EXTENSION_CRITERION, false, false,true));
        assert D.equals(files[0]);
        assert B.equals(files[1]);
        assert C.equals(files[2]);
        assert A.equals(files[3]);
    }

    @Test
    public void testCompareExtDesc() {
        Arrays.sort(files, new FileComparator(FileComparator.EXTENSION_CRITERION, false, false,false));
        assert C.equals(files[0]);
        assert D.equals(files[1]);
        assert B.equals(files[2]);
        assert A.equals(files[3]);
    }
    
}
