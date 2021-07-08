/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.session.tests;

import static org.apache.geode.session.tests.TomcatInstall.TomcatVersion.TOMCAT7;
import static org.apache.geode.session.tests.TomcatInstall.TomcatVersion.TOMCAT8;
import static org.apache.geode.session.tests.TomcatInstall.TomcatVersion.TOMCAT9_0_20;
import static org.apache.geode.session.tests.TomcatInstall.TomcatVersion.TOMCAT9_RECENT;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import org.codehaus.cargo.container.ContainerException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.geode.internal.UniquePortSupplier;
import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.management.internal.i18n.CliStrings;
import org.apache.geode.test.junit.categories.BackwardCompatibilityTest;
import org.apache.geode.test.junit.rules.GfshCommandRule;
import org.apache.geode.test.junit.runners.CategoryWithParameterizedRunnerFactory;
import org.apache.geode.test.version.TestVersion;
import org.apache.geode.test.version.VersionManager;

/**
 * This test iterates through the versions of Geode and executes session client compatibility with
 * the current version of Geode.
 */
@Category({BackwardCompatibilityTest.class})
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CategoryWithParameterizedRunnerFactory.class)
public abstract class TomcatSessionBackwardsCompatibilityTestBase {
  private final UniquePortSupplier portSupplier = new UniquePortSupplier();

  @Parameterized.Parameters
  public static Collection<String> data() {
    List<String> result = VersionManager.getInstance().getVersionsWithoutCurrent();
    result.removeIf(s -> TestVersion.compare(s, "1.2.0") < 0);
    if (result.size() < 1) {
      throw new RuntimeException("No older versions of Geode were found to test against");
    }
    return result;
  }

  @Rule
  public transient GfshCommandRule gfsh = new GfshCommandRule();

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule
  public transient TestName testName = new TestName();

  protected transient Client client;
  protected transient ContainerManager manager;

  protected File oldBuild;
  protected File oldModules;

  protected TomcatInstall tomcat7AndOldModules;
  protected TomcatInstall tomcat7AndCurrentModules;
  protected TomcatInstall tomcat8AndOldModules;
  protected TomcatInstall tomcat8AndCurrentModules;
  protected TomcatInstall tomcat9AndOldModules;
  protected TomcatInstall tomcat9AndCurrentModules;

  protected int locatorPort;
  protected String classPathTomcat7;
  protected String classPathTomcat8;
  protected String classPathTomcat9;
  protected String serverDir;
  protected String locatorDir;
  protected final String oldVersion;

  protected TomcatSessionBackwardsCompatibilityTestBase(String version) {
    VersionManager versionManager = VersionManager.getInstance();
    String installLocation = versionManager.getInstall(version);
    oldVersion = version;
    oldBuild = new File(installLocation);
    oldModules = new File(installLocation + "/tools/Modules/");
  }

  protected void startServer(String classPath, int locatorPort) throws Exception {
    serverDir = tempFolder.newFolder("server").getPath();
    CommandStringBuilder command = new CommandStringBuilder(CliStrings.START_SERVER);
    command.addOption(CliStrings.START_SERVER__NAME, "server");
    command.addOption(CliStrings.START_SERVER__SERVER_PORT, "0");
    command.addOption(CliStrings.START_SERVER__CLASSPATH, classPath);
    command.addOption(CliStrings.START_SERVER__LOCATORS, "localhost[" + locatorPort + "]");
    command.addOption(CliStrings.START_SERVER__DIR, serverDir);
    gfsh.executeAndAssertThat(command.toString()).statusIsSuccess();
  }

  protected void startLocator(String classPath, int port) throws Exception {
    locatorDir = tempFolder.newFolder("locator").getPath();
    CommandStringBuilder locStarter = new CommandStringBuilder(CliStrings.START_LOCATOR);
    locStarter.addOption(CliStrings.START_LOCATOR__MEMBER_NAME, "loc");
    locStarter.addOption(CliStrings.START_LOCATOR__CLASSPATH, classPath);
    locStarter.addOption(CliStrings.START_LOCATOR__PORT, Integer.toString(port));
    locStarter.addOption(CliStrings.START_LOCATOR__DIR, locatorDir);
    gfsh.executeAndAssertThat(locStarter.toString()).statusIsSuccess();
  }

  @Before
  public void setup() throws Exception {
    TomcatInstall.TomcatVersion tomcat9Version = TOMCAT9_0_20;
    // If Geode version is 1.12.2 or 1.13.3+, Tomcat 9 version > 9.0.20 may be used
    if (TestVersion.compare(oldVersion, "1.13.2") > 0 ||
        (TestVersion.compare(oldVersion, "1.13.0") < 0
            && TestVersion.compare(oldVersion, "1.12.1") > 0)) {
      tomcat9Version = TOMCAT9_RECENT;
    }

    tomcat7AndOldModules =
        new TomcatInstall("Tomcat7AndOldModules", TOMCAT7,
            ContainerInstall.ConnectionType.CLIENT_SERVER,
            oldModules.getAbsolutePath(), oldBuild.getAbsolutePath() + "/lib",
            portSupplier::getAvailablePort, TomcatInstall.CommitValve.DEFAULT);

    tomcat7AndCurrentModules =
        new TomcatInstall("Tomcat7AndCurrentModules", TOMCAT7,
            ContainerInstall.ConnectionType.CLIENT_SERVER,
            portSupplier::getAvailablePort, TomcatInstall.CommitValve.DEFAULT);

    tomcat8AndOldModules =
        new TomcatInstall("Tomcat8AndOldModules", TOMCAT8,
            ContainerInstall.ConnectionType.CLIENT_SERVER,
            oldModules.getAbsolutePath(),
            oldBuild.getAbsolutePath() + "/lib",
            portSupplier::getAvailablePort, TomcatInstall.CommitValve.DEFAULT);

    tomcat8AndCurrentModules =
        new TomcatInstall("Tomcat8AndCurrentModules", TOMCAT8,
            ContainerInstall.ConnectionType.CLIENT_SERVER,
            portSupplier::getAvailablePort, TomcatInstall.CommitValve.DEFAULT);

    tomcat9AndOldModules =
        new TomcatInstall("Tomcat9AndOldModules", tomcat9Version,
            ContainerInstall.ConnectionType.CLIENT_SERVER,
            oldModules.getAbsolutePath(),
            oldBuild.getAbsolutePath() + "/lib",
            portSupplier::getAvailablePort, TomcatInstall.CommitValve.DEFAULT);

    tomcat9AndCurrentModules =
        new TomcatInstall("Tomcat9AndCurrentModules", TOMCAT9_RECENT,
            ContainerInstall.ConnectionType.CLIENT_SERVER,
            portSupplier::getAvailablePort, TomcatInstall.CommitValve.DEFAULT);

    classPathTomcat7 = tomcat7AndCurrentModules.getHome() + "/lib/*" + File.pathSeparator
        + tomcat7AndCurrentModules.getHome() + "/bin/*";
    classPathTomcat8 = tomcat8AndCurrentModules.getHome() + "/lib/*" + File.pathSeparator
        + tomcat8AndCurrentModules.getHome() + "/bin/*";
    classPathTomcat9 = tomcat9AndCurrentModules.getHome() + "/lib/*" + File.pathSeparator
        + tomcat9AndCurrentModules.getHome() + "/bin/*";

    // Get available port for the locator
    locatorPort = portSupplier.getAvailablePort();

    tomcat7AndOldModules.setDefaultLocatorPort(locatorPort);
    tomcat7AndCurrentModules.setDefaultLocatorPort(locatorPort);

    tomcat8AndOldModules.setDefaultLocatorPort(locatorPort);
    tomcat8AndCurrentModules.setDefaultLocatorPort(locatorPort);

    tomcat9AndOldModules.setDefaultLocatorPort(locatorPort);
    tomcat9AndCurrentModules.setDefaultLocatorPort(locatorPort);

    client = new Client();
    manager = new ContainerManager();
    // Due to parameterization of the test name, the URI would be malformed. Instead, it strips off
    // the [] symbols
    manager.setTestName(testName.getMethodName().replace("[", "").replace("]", ""));
  }

  protected void startClusterWithTomcat(String tomcatClassPath) throws Exception {
    startLocator(tomcatClassPath, locatorPort);
    startServer(tomcatClassPath, locatorPort);
  }

  /**
   * Stops all containers that were previously started and cleans up their configurations
   */
  @After
  public void stop() throws Exception {
    try {
      manager.stopAllActiveContainers();
    } catch (ContainerException e) {
      // container might fail to start within certain timeframe for older Geode versions due to
      // classcast exception that was fixed in later versions
    }
    manager.cleanUp();

    CommandStringBuilder command = new CommandStringBuilder(CliStrings.STOP_SERVER);
    command.addOption(CliStrings.STOP_SERVER__DIR, serverDir);
    gfsh.executeAndAssertThat(command.toString()).statusIsSuccess();

    CommandStringBuilder locStop = new CommandStringBuilder(CliStrings.STOP_LOCATOR);
    locStop.addOption(CliStrings.STOP_LOCATOR__DIR, locatorDir);
    gfsh.executeAndAssertThat(locStop.toString()).statusIsSuccess();
  }

  protected void doPutAndGetSessionOnAllClients() throws IOException, URISyntaxException {
    // This has to happen at the start of every test
    manager.startAllInactiveContainers();

    String key = "value_testSessionPersists";
    String value = "Foo";

    client.setPort(Integer.parseInt(manager.getContainerPort(0)));
    Client.Response resp = client.set(key, value);
    String cookie = resp.getSessionCookie();

    for (int i = 0; i < manager.numContainers(); i++) {
      System.out.println("Checking get for container:" + i);
      client.setPort(Integer.parseInt(manager.getContainerPort(i)));
      resp = client.get(key);

      assertEquals("Sessions are not replicating properly", cookie, resp.getSessionCookie());
      assertEquals("Session data is not replicating properly", value, resp.getResponse());
    }
  }

}
