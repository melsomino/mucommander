package com.mucommander.commons.file.filter;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.util.FileSet;

/**
 * A <code>FileFilter</code> matches files that meet certain criteria. It can operate in two opposite modes: inverted
 * and non-inverted. By default, a <code>FileFilter</code> operates in non-inverted mode where
 * {@link #match(AbstractFile)} returns the value of {@link #accept(AbstractFile)}. On the contrary, when operating in
 * inverted mode, {@link #match(AbstractFile)} returns the value of {@link #reject(AbstractFile)}. It is important to
 * understand that {@link #accept(AbstractFile)} and {@link #reject(AbstractFile)} are not affected by the inverted
 * mode in which a filter operates.
 *
 * <p>Several convenience methods are provided to operate this filter on a set of files, and filter out files that
 * do not match this filter.
 *
 * <p>A <code>FileFilter</code> instance can be passed to {@link AbstractFile#ls(FileFilter)} to filter out some of the
 * the files contained by a folder.
 *
 * @see AbstractFileFilter
 * @see FilenameFilter
 * @see com.mucommander.commons.file.AbstractFile#ls(FileFilter)
 * @author Maxence Bernard
 */
public interface FileFilter {

    /**
     * Return <code>true</code> if this filter operates in normal mode, <code>false</code> if in inverted mode.
     *
     * @return true if this filter operates in normal mode, false if in inverted mode
     */
    boolean isInverted();

    /**
     * Sets the mode in which {@link #match(com.mucommander.commons.file.AbstractFile)} operates. If <code>true</code>, this
     * filter will operate in inverted mode: files that would be accepted by {@link #match(com.mucommander.commons.file.AbstractFile)}
     * in normal (non-inverted) mode will be rejected, and vice-versa.<br>
     * The inverted mode has no effect on the values returned by {@link #accept(com.mucommander.commons.file.AbstractFile)} and
     * {@link #reject(com.mucommander.commons.file.AbstractFile)}.
     *
     * @param inverted if true, this filter will operate in inverted mode.
     */
    void setInverted(boolean inverted);

    /**
     * Returns <code>true</code> if this filter matched the given file, according to the current {@link #isInverted()}
     * mode:
     * <ul>
     *  <li>if this filter currently operates in normal (non-inverted) mode, this method will return the value of {@link #accept(com.mucommander.commons.file.AbstractFile)}</li>
     *  <li>if this filter currently operates in inverted mode, this method will return the value of {@link #reject(com.mucommander.commons.file.AbstractFile)}</li>
     * </ul>
     *
     * @param file the file to test
     * @return true if this filter matched the given file, according to the current inverted mode
     */
    boolean match(AbstractFile file);

    /**
     * Returns <code>true</code> if the given file was rejected by this filter, <code>false</code> if it was accepted.
     *
     * <p>The {@link #isInverted() inverted} mode has no effect on the values returned by this method.
     *
     * @param file the file to test
     * @return true if the given file was rejected by this FileFilter
     */
    boolean reject(AbstractFile file);

    /**
     * Convenience method that filters out files that do not {@link #match(AbstractFile) match} this filter and
     * returns a file array of matched <code>AbstractFile</code> instances.
     *
     * @param files files to be tested against {@link #match(com.mucommander.commons.file.AbstractFile)}
     * @return a file array of files that were matched by this filter
     */
    AbstractFile[] filter(AbstractFile files[]);

    /**
     * Convenience method that filters out files that do not {@link #match(AbstractFile) match} this filter
     * and removes them from the given {@link FileSet}.
     *
     * @param files files to be tested against {@link #match(com.mucommander.commons.file.AbstractFile)}
     */
    void filter(FileSet files);

    /**
     * Convenience method that returns <code>true</code> if all the files contained in the specified file array
     * were matched by {@link #match(AbstractFile)}, <code>false</code> if one of the files wasn't.
     *
     * @param files the files to test against this FileFilter
     * @return true if all the files contained in the specified file array were matched by this filter
     */
    boolean match(AbstractFile files[]);

    /**
     * Convenience method that returns <code>true</code> if all the files contained in the specified {@link FileSet}
     * were matched by {@link #match(AbstractFile)}, <code>false</code> if one of the files wasn't.
     *
     * @param files the files to test against this FileFilter
     * @return true if all the files contained in the specified {@link FileSet} were matched by this filter
     */
    boolean match(FileSet files);

    /**
     * Convenience method that returns <code>true</code> if all the files contained in the specified file array
     * were accepted by {@link #accept(AbstractFile)}, <code>false</code> if one of the files wasn't.
     *
     * @param files the files to test against this FileFilter
     * @return true if all the files contained in the specified file array were accepted by this filter
     */
    boolean accept(AbstractFile files[]);

    /**
     * Convenience method that returns <code>true</code> if all the files contained in the specified {@link FileSet}
     * were accepted by {@link #accept(AbstractFile)}, <code>false</code> if one of the files wasn't.
     *
     * @param files the files to test against this FileFilter
     * @return true if all the files contained in the specified {@link FileSet} were accepted by this filter
     */
    boolean accept(FileSet files);

    /**
     * Convenience method that returns <code>true</code> if all the files contained in the specified file array
     * were rejected by {@link #reject(AbstractFile)}, <code>false</code> if one of the files wasn't.
     *
     * @param files the files to test against this FileFilter
     * @return true if all the files contained in the specified file array were rejected by this filter
     */
    boolean reject(AbstractFile files[]);

    /**
     * Convenience method that returns <code>true</code> if all the files contained in the specified {@link FileSet}
     * were rejected by {@link #reject(AbstractFile)}, <code>false</code> if one of the files wasn't.
     *
     * @param files the files to test against this FileFilter
     * @return true if all the files contained in the specified {@link FileSet} were rejected by this filter
     */
    boolean reject(FileSet files);

    /**
     * Returns <code>true</code> if the given file was accepted by this filter, <code>false</code> if it was rejected.
     *
     * <p>The {@link #isInverted() inverted} mode has no effect on the values returned by this method.
     *
     * @param file the file to test
     * @return true if the given file was accepted by this FileFilter
     */
    boolean accept(AbstractFile file);
}
