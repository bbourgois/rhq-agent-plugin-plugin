/*
 * RHQ Management Platform
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.rhq.maven.plugins;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.zeroturnaround.zip.ZipUtil;

/**
 * @author Thomas Segismont
 */
public abstract class AbstractExecCliMojo extends AbstractMojo {

    private static final String RHQ_CLI_MODULE_GROUP_ID = "org.rhq";
    private static final String RHQ_CLI_MODULE_ARTIFACT_ID = "rhq-remoting-cli";
    private static final String RHQ_CLI_SCRIPT_WINDOWS = "rhq-cli.bat";
    private static final String RHQ_CLI_SCRIPT_OTHERS = "rhq-cli.sh";

    /**
     * The build directory (root of build works).
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    protected File buildDirectory;

    /**
     * The CLI directory (where to install the RHQ CLI).
     */
    @Parameter(defaultValue = "${project.build.directory}/rhq-cli", required = true)
    protected File rhqCliDirectory;

    /**
     * Version of the RHQ CLI.
     */
    @Parameter(required = true)
    protected String rhqVersion;

    /**
     * Whether to login to a remote RHQ server.
     */
    @Parameter(defaultValue = "false")
    protected boolean login;

    /**
     * Remote RHQ server host. Required if <code>login</code> is set to true.
     */
    @Parameter(required = false)
    protected String host;

    /**
     * Remote RHQ server port.
     */
    @Parameter(defaultValue = "7080")
    protected int port;

    /**
     * Authentication user name. Required if <code>login</code> is set to true.
     */
    @Parameter(required = false)
    protected String username;

    /**
     * Authentication password. Required if <code>login</code> is set to true.
     */
    @Parameter(required = false)
    protected String password;

    /**
     * Whether to fail the build if an error occurs while uploading.
     */
    @Parameter(defaultValue = "true")
    protected boolean failOnError;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", required = true, readonly = true)
    private List remoteRepositories;

    @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
    private ArtifactRepository localRepository;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactResolver artifactResolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (shouldSkip()) {
            getLog().info("Skipped execution");
        }
        validateCommonParams();
        validateParams();
        installRhqCLi();
        doExecute();
    }

    protected abstract boolean shouldSkip();

    private void validateCommonParams() throws MojoExecutionException {
        if (login) {
            if (StringUtils.isBlank(host)) {
                throw new MojoExecutionException("'host' param is blank");
            }
            if (StringUtils.isBlank(username)) {
                throw new MojoExecutionException("'username' param is blank");
            }
            if (StringUtils.isBlank(password)) {
                throw new MojoExecutionException("'password' param is blank");
            }
        }
    }

    protected abstract void validateParams() throws MojoExecutionException, MojoFailureException;

    private void installRhqCLi() throws MojoExecutionException {
        Artifact rhqCliZipArtifact = this.artifactFactory.createArtifact(
                RHQ_CLI_MODULE_GROUP_ID, RHQ_CLI_MODULE_ARTIFACT_ID, rhqVersion,
                null, "zip");
        try {
            artifactResolver.resolve(rhqCliZipArtifact, remoteRepositories, localRepository);
            ZipUtil.unpack(rhqCliZipArtifact.getFile(), rhqCliDirectory);
            if (getLog().isDebugEnabled()) {
                getLog().debug("Installed RHQ CLI");
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    protected File getRhqCliStartScriptFile() throws IOException {
        String scriptName;
        if (System.getProperty("os.name").toLowerCase().indexOf("win") != -1) {
            scriptName = RHQ_CLI_SCRIPT_WINDOWS;
        } else {
            scriptName = RHQ_CLI_SCRIPT_OTHERS;
        }
        List files = FileUtils.getFileNames(rhqCliDirectory, "**/" + scriptName, null, false, true);
        if (files.size() == 1) {
            String filename = (String) files.get(0);
            if (getLog().isDebugEnabled()) {
                getLog().debug("Found RHQ CLI start script: " + filename);
            }
            File file = new File(rhqCliDirectory, filename);
            if (!file.canExecute()) {
                file.setExecutable(true);
            }
            return file;
        } else if (files.size() > 1) {
            getLog().warn("Found more than one RHQ CLI start Script: " + files);
        }
        return null;
    }

    protected void handleFailure(String message) throws MojoFailureException {
        if (failOnError) {
            throw new MojoFailureException(message);
        }
        getLog().error(message);
    }

    protected void handleException(Exception e) throws MojoExecutionException {
        if (failOnError) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        getLog().error(e.getMessage(), e);
    }
}
