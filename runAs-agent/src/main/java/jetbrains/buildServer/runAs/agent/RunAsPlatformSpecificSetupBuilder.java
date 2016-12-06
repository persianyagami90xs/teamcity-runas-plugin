package jetbrains.buildServer.runAs.agent;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.dotNet.buildRunner.agent.*;
import jetbrains.buildServer.runAs.common.Constants;
import org.jetbrains.annotations.NotNull;

public class RunAsPlatformSpecificSetupBuilder implements CommandLineSetupBuilder {
  static final String TOOL_FILE_NAME = "runAs";
  static final String ARGS_EXT = ".args";
  private final SettingsProvider mySettingsProvider;
  private final RunnerParametersService myParametersService;
  private final FileService myFileService;
  private final ResourcePublisher myBeforeBuildPublisher;
  private final AccessControlResource myAccessControlResource;
  private final ResourceGenerator<Settings> mySettingsGenerator;
  private final ResourceGenerator<Params> myRunAsCmdGenerator;
  private final CommandLineArgumentsService myCommandLineArgumentsService;
  private final FileAccessService myFileAccessService;
  private final String myCommandFileExtension;

  public RunAsPlatformSpecificSetupBuilder(
    @NotNull final SettingsProvider settingsProvider,
    @NotNull final RunnerParametersService parametersService,
    @NotNull final FileService fileService,
    @NotNull final ResourcePublisher beforeBuildPublisher,
    @NotNull final AccessControlResource accessControlResource,
    @NotNull final ResourceGenerator<Settings> settingsGenerator,
    @NotNull final ResourceGenerator<Params> runAsCmdGenerator,
    @NotNull final CommandLineArgumentsService commandLineArgumentsService,
    @NotNull final FileAccessService fileAccessService,
    @NotNull final String commandFileExtension) {
    mySettingsProvider = settingsProvider;
    myParametersService = parametersService;
    myFileService = fileService;
    myBeforeBuildPublisher = beforeBuildPublisher;
    myAccessControlResource = accessControlResource;
    mySettingsGenerator = settingsGenerator;
    myRunAsCmdGenerator = runAsCmdGenerator;
    myCommandLineArgumentsService = commandLineArgumentsService;
    myFileAccessService = fileAccessService;
    myCommandFileExtension = commandFileExtension;
  }

  @NotNull
  @Override
  public Iterable<CommandLineSetup> build(@NotNull final CommandLineSetup commandLineSetup) {
    // Get settings
    final Settings settings = mySettingsProvider.tryGetSettings();
    if(settings == null) {
      return Collections.singleton(commandLineSetup);
    }

    // Resources
    final ArrayList<CommandLineResource> resources = new ArrayList<CommandLineResource>();
    resources.addAll(commandLineSetup.getResources());

    // Settings
    final File settingsFile = myFileService.getTempFileName(ARGS_EXT);
    resources.add(new CommandLineFile(myBeforeBuildPublisher, settingsFile.getAbsoluteFile(), mySettingsGenerator.create(settings)));

    // Command
    List<CommandLineArgument> cmdLineArgs = new ArrayList<CommandLineArgument>();
    cmdLineArgs.add(new CommandLineArgument(commandLineSetup.getToolPath(), CommandLineArgument.Type.PARAMETER));
    cmdLineArgs.addAll(commandLineSetup.getArgs());

    final Params params = new Params(myCommandLineArgumentsService.createCommandLineString(cmdLineArgs));

    final File commandFile = myFileService.getTempFileName(myCommandFileExtension);
    resources.add(new CommandLineFile(myBeforeBuildPublisher, commandFile.getAbsoluteFile(), myRunAsCmdGenerator.create(params)));

    myAccessControlResource.setAccess(
      new AccessControlList(Arrays.asList(
        new AccessControlEntry(commandFile, AccessControlAccount.getAll(), null, null, true, null),
        new AccessControlEntry(myFileService.getCheckoutDirectory(), AccessControlAccount.getAll(), true, null, null, true),
        new AccessControlEntry(myFileService.getTempDirectory(), AccessControlAccount.getAll(), true, null, null, true))));
    resources.add(myAccessControlResource);

    return Collections.singleton(
      new CommandLineSetup(
        getTool().getAbsolutePath(),
        Arrays.asList(
          new CommandLineArgument(settingsFile.getAbsolutePath(), CommandLineArgument.Type.PARAMETER),
          new CommandLineArgument(commandFile.getAbsolutePath(), CommandLineArgument.Type.PARAMETER),
          new CommandLineArgument(settings.getPassword(), CommandLineArgument.Type.PARAMETER)),
        resources));
  }

  private File getTool() {
    final File path = new File(myParametersService.getToolPath(Constants.RUN_AS_TOOL_NAME), TOOL_FILE_NAME + myCommandFileExtension);
    myFileService.validatePath(path);
    final AccessControlList acl = new AccessControlList(Arrays.asList(new AccessControlEntry(path, AccessControlAccount.getCurrent(), null, null, true, null)));
    myFileAccessService.setAccess(acl);
    return path;
  }
}