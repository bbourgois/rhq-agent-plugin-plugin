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
import java.util.Iterator;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;

import static org.codehaus.plexus.util.FileUtils.copyFileToDirectory;
import static org.codehaus.plexus.util.FileUtils.forceDelete;
import static org.rhq.maven.plugins.Utils.getAgentPluginArchiveFile;
import static org.rhq.maven.plugins.Utils.isAgentPlugin;

/**
 * Package a freshly built RHQ Agent Plugin.
 *
 * @author Thomas Segismont
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution =
        ResolutionScope.RUNTIME, threadSafe = true)
public class PackageMojo extends AbstractMojo {

    /**
     * The build directory (root of build works)
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    private File buildDirectory;

    /**
     * The output directory (where standard plugins put compiled classes and resources)
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
    private File outputDirectory;

    /**
     * The lib directory (where to copy the agent plugin dependencies)
     */
    @Parameter(defaultValue = "${project.build.directory}/lib", required = true)
    private File libDirectory;

    /**
     * The name of the generated RHQ agent plugin archive
     */
    @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
    private String finalName;

    /**
     * This will allow to get our plugin configured like any archiver plugin
     * <p/>
     * See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven Archiver Reference</a>.
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    @Component(role = Archiver.class, hint = "jar")
    private JarArchiver jarArchiver;

    @Component
    private MavenProject project;

    @Component
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Create the package and set it as the main project artifact
        File agentPluginArchive = createAgentPluginArchive();
        project.getArtifact().setFile(agentPluginArchive);
    }

    private File createAgentPluginArchive() throws MojoExecutionException {

        // Get the Java IO File denoting the project package
        File agentPluginArchive = getAgentPluginArchiveFile(buildDirectory, finalName);

        // Configure the Maven archiver to use JAR archive utility
        MavenArchiver archiver = new MavenArchiver();
        archiver.setArchiver(jarArchiver);
        archiver.setOutputFile(agentPluginArchive);

        if (libDirectory.exists()) {
            // Clean the lib working directory
            try {
                forceDelete(libDirectory);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to delete " + libDirectory, e);
            }
        }

        try {

            // Request compiled classes to be added to the archive
            archiver.getArchiver().addDirectory(outputDirectory);
            getLog().info("Added " + outputDirectory + " content to the plugin archive");

            // Now request JAR dependencies of scope runtime to get included
            // This call to #getArtifacts only works because the mojo requires dependency resolution of scope RUNTIME
            Iterator projectArtifacts = project.getArtifacts().iterator();
            ScopeArtifactFilter artifactFilter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
            while (projectArtifacts.hasNext()) {
                Artifact artifact = (Artifact) projectArtifacts.next();
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Found project artifact: " + artifact);
                }
                if (!artifact.isOptional() && artifact.getType().equals("jar") && artifactFilter.include(artifact)) {
                    getLog().info("Added " + artifact + " library to the plugin archive");
                    copyFileToDirectory(artifact.getFile(), libDirectory);
                    if (isAgentPlugin(artifact.getFile())) {
                        getLog().warn(artifact.getFile().getName() + " is an agent plugin and should not be shipped " +
                                "with your plugin");
                    }
                }
            }
            // This directory will exist only if at least one dependency was added
            if (libDirectory.exists()) {
                // Request all found runtime dependencies to be added to the archive under the 'lib' directory
                archiver.getArchiver().addDirectory(libDirectory, "lib/");
            } else {
                getLog().info("No libraries added to the plugin archive");
            }

            archiver.createArchive(session, project, archive);

        } catch (Exception e) {
            throw new MojoExecutionException("Could not create agent plugin archive", e);
        }

        return agentPluginArchive;
    }
}
