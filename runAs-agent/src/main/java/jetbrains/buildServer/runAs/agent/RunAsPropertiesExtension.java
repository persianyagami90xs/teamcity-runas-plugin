package jetbrains.buildServer.runAs.agent;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.dotNet.buildRunner.agent.BuildRunnerContextProvider;
import jetbrains.buildServer.dotNet.buildRunner.agent.CommandLineArgument;
import jetbrains.buildServer.dotNet.buildRunner.agent.CommandLineResource;
import jetbrains.buildServer.dotNet.buildRunner.agent.CommandLineSetup;
import jetbrains.buildServer.runAs.common.Constants;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.positioning.PositionAware;
import jetbrains.buildServer.util.positioning.PositionConstraint;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.runAs.agent.Constants.*;
import static jetbrains.buildServer.runAs.common.Constants.RUN_AS_TOOL_NAME;

@SuppressWarnings("deprecation")
public class RunAsPropertiesExtension extends AgentLifeCycleAdapter implements RunAsAccessService, PositionAware {
  private static final String TOOL_FILE_NAME_LINUX = "runAs.sh";
  private static final Logger LOG = Logger.getInstance(RunAsPropertiesExtension.class.getName());
  private static final String[] OurProtectedParams = new String[] { Constants.PASSWORD, Constants.CONFIG_PASSWORD };
  private static final CommandLineSetup OurIcaclsCmdLineSetup = new CommandLineSetup(ICACLS_TOOL_NAME, Collections.<CommandLineArgument>emptyList(), Collections.<CommandLineResource>emptyList());
  private static final CommandLineSetup OurChmodHelpCmdLineSetup = new CommandLineSetup(CHMOD_TOOL_NAME, Arrays.asList(new CommandLineArgument("--help", CommandLineArgument.Type.PARAMETER)), Collections.<CommandLineResource>emptyList());
  private static final CommandLineSetup OurSuCmdLineSetup = new CommandLineSetup(SU_TOOL_NAME, Arrays.asList(new CommandLineArgument("--help", CommandLineArgument.Type.PARAMETER)), Collections.<CommandLineResource>emptyList());
  private final ToolProvidersRegistry myToolProvidersRegistry;
  private final BuildRunnerContextProvider myBuildRunnerContextProvider;
  private final CommandLineExecutor myCommandLineExecutor;
  private final ProfileParametersService myProfileParametersService;
  private final FileAccessCacheManager myBuildFileAccessCacheManager;
  private boolean myIsRunAsEnabled;
  private boolean myIsHidingOfPropertyIsNotSupported;

  public RunAsPropertiesExtension(
    @NotNull final EventDispatcher<AgentLifeCycleListener> events,
    @NotNull final ToolProvidersRegistry toolProvidersRegistry,
    @NotNull final BuildRunnerContextProvider buildRunnerContextProvider,
    @NotNull final CommandLineExecutor commandLineExecutor,
    @NotNull final ProfileParametersService profileParametersService,
    @NotNull final FileAccessCacheManager buildFileAccessCacheManager) {
    myToolProvidersRegistry = toolProvidersRegistry;
    myBuildRunnerContextProvider = buildRunnerContextProvider;
    myCommandLineExecutor = commandLineExecutor;
    myProfileParametersService = profileParametersService;
    myBuildFileAccessCacheManager = buildFileAccessCacheManager;
    events.addListener(this);
  }

  @Override
  public boolean getIsRunAsEnabled() {
    return myIsRunAsEnabled;
  }

  @NotNull
  @Override
  public String getOrderId() {
    return "";
  }

  @NotNull
  @Override
  public PositionConstraint getConstraint() {
    return PositionConstraint.first();
  }

  @Override
  public void agentInitialized(@NotNull final BuildAgent agent) {
    updateIsRunAsEnabled(agent.getConfiguration());
    super.agentInitialized(agent);
  }

  @Override
  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    myBuildRunnerContextProvider.initialize(((AgentRunningBuildEx)runningBuild).getCurrentRunnerContext());
    protectProperties(runningBuild);
    super.buildStarted(runningBuild);
  }

  @Override
  public void buildFinished(@NotNull final AgentRunningBuild build, @NotNull final BuildFinishedStatus buildStatus) {
    super.buildFinished(build, buildStatus);
    myBuildFileAccessCacheManager.reset();
  }

  private void updateIsRunAsEnabled(final @NotNull BuildAgentConfiguration config) {
    myIsRunAsEnabled = false;

    final ToolProvider toolProvider = myToolProvidersRegistry.findToolProvider(RUN_AS_TOOL_NAME);
    if (toolProvider == null) {
      LOG.warn("Can not find tool " + RUN_AS_TOOL_NAME);
      return;
    }

    if(SystemInfo.isWindows) {
      try {
        myCommandLineExecutor.runProcess(OurIcaclsCmdLineSetup, 600);
      } catch (ExecutionException e) {
        LOG.warn(ICACLS_TOOL_NAME + " is not supported");
        return;
      }

      final String pathToRunAsPlugin = toolProvider.getPath(RUN_AS_TOOL_NAME);
      final String runAsToolPath = new File("x86", RUN_AS_WIN32_TOOL_NAME).getPath();
      final CommandLineSetup cmdLineSetup = new CommandLineSetup(
        new File(pathToRunAsPlugin, runAsToolPath).getAbsolutePath(),
        Arrays.asList(new CommandLineArgument("-t", CommandLineArgument.Type.PARAMETER)),
        Collections.<CommandLineResource>emptyList());

      try {
        final ExecResult result = myCommandLineExecutor.runProcess(cmdLineSetup, 600);
        if (result != null) {
          LOG.info(RUN_AS_WIN32_TOOL_NAME + " self-test exit code: " + result.getExitCode());
          final int bitness = result.getExitCode();
          if (bitness == 32 || bitness == 64) {
            myIsRunAsEnabled = true;
            config.addConfigurationParameter(Constants.RUN_AS_ENABLED, Boolean.toString(true));
          } else {
            LOG.warn("Invalid " + RUN_AS_WIN32_TOOL_NAME + " exit code: " + bitness);
          }
        }
      } catch (ExecutionException e) {
        LOG.warn(RUN_AS_WIN32_TOOL_NAME + " is not supported");
      }
    }
    else
    {
      try {
        myCommandLineExecutor.runProcess(OurChmodHelpCmdLineSetup, 600);
      } catch (ExecutionException e) {
        LOG.warn(CHMOD_TOOL_NAME + " is not supported");
        return;
      }

      try {
        myCommandLineExecutor.runProcess(OurSuCmdLineSetup, 600);
      } catch (ExecutionException e) {
        LOG.warn(SU_TOOL_NAME + " is not supported");
        return;
      }

      final File pathToRunAsScript = new File(toolProvider.getPath(RUN_AS_TOOL_NAME), TOOL_FILE_NAME_LINUX);
      final CommandLineSetup scriptCmd = new CommandLineSetup(pathToRunAsScript.getAbsolutePath(), Collections.<CommandLineArgument>emptyList(), Collections.<CommandLineResource>emptyList());
      try {
        final ExecResult res = myCommandLineExecutor.runProcess(scriptCmd, 600);
        if(res.getExitCode() != 0) {
          LOG.warn("RunAs is not supported");
          return;
        }
      } catch (ExecutionException e) {
        LOG.warn(CHMOD_TOOL_NAME + " is not supported");
        return;
      }

      myIsRunAsEnabled = true;
      config.addConfigurationParameter(Constants.RUN_AS_ENABLED, Boolean.toString(true));
    }
  }

  private void protectProperties(final @NotNull AgentRunningBuild runningBuild) {
    myProfileParametersService.load();
    final Set<String> propertySets = myProfileParametersService.getProfiles();
    for (final String protectedPropertyName: OurProtectedParams) {
      // Properties
      for (String propertySet: propertySets) {
        final String propertyValue = myProfileParametersService.tryGetProperty(propertySet, protectedPropertyName);
        if(StringUtil.isEmptyOrSpaces(propertyValue)) {
          continue;
        }

        protectProperty(runningBuild, propertyValue);
      }
    }
  }

  private void protectProperty(
    final @NotNull AgentRunningBuild runningBuild,
    final String propertyValue) {
    if(myIsHidingOfPropertyIsNotSupported) {
      return;
    }

    try {
      final Method getPasswordReplacerMethod = AgentRunningBuild.class.getMethod("getPasswordReplacer");
      if(getPasswordReplacerMethod == null) {
        onHidingOfPropertyIsNotSupportedMessage();
        return;
      }

      Object passwordReplacer = getPasswordReplacerMethod.invoke(runningBuild);
      if(passwordReplacer == null) {
        onHidingOfPropertyIsNotSupportedMessage();
        return;
      }

      final Class<?> passwordReplacerClass = Class.forName("jetbrains.buildServer.util.PasswordReplacer");
      if(passwordReplacerClass == null) {
        onHidingOfPropertyIsNotSupportedMessage();
        return;
      }

      final Method addPasswordMethod = passwordReplacerClass.getMethod("addPassword", String.class);
      if(addPasswordMethod == null) {
        onHidingOfPropertyIsNotSupportedMessage();
        return;
      }

      addPasswordMethod.invoke(passwordReplacer, propertyValue);
    } catch(Exception ignored) {
      onHidingOfPropertyIsNotSupportedMessage();
    }
  }

  private void onHidingOfPropertyIsNotSupportedMessage() {
    myIsHidingOfPropertyIsNotSupported = true;
    LOG.debug("Hiding of property is not yet supported.");
  }
}