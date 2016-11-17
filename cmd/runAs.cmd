@ECHO OFF
SETLOCAL
SET /A ARGS_COUNT=0    
FOR %%A in (%*) DO SET /A "ARGS_COUNT+=1"
IF %ARGS_COUNT% NEQ 3 (
	@ECHO Invalid arguments.
	@ECHO Usage: runAs.cmd settings_file_name command_file_name password
	@EXIT -1
)
ENDLOCAL

REM Define OS bitness
SET "RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_BIN=%~dp0"
SET "RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_TOOL=JetBrains.runAs.exe"
"%RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_BIN%x86\%RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_TOOL%" -t -l:errors
SET "RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_OS_BITNESS=%errorlevel%"

IF %RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_OS_BITNESS% EQU 64 (
	SET "RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_TOOL=%RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_BIN%x64\%RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_TOOL%"
	GOTO RUN_AS
)

IF %RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_OS_BITNESS% EQU 32 (
	SET "RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_TOOL=%RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_BIN%x86\%RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_TOOL%"
	GOTO RUN_AS
)

SET "EXIT_CODE=%RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_OS_BITNESS%"
ECHO.
IF %EXIT_CODE% EQU 1 ECHO ##teamcity[message text='Invoker has no administrative privileges, when running under the Windows service.' status='ERROR']
IF %EXIT_CODE% EQU 2 ECHO ##teamcity[message text='Invoker has no SeAssignPrimaryTokenPrivilege (Replace a process-level token), when running under the Windows service.' status='ERROR']
IF %EXIT_CODE% EQU 3 ECHO ##teamcity[message text='Invoker has no SeTcbPrivilege (Act as part of the operating system), when running under the Windows service.' status='ERROR']
EXIT /B %EXIT_CODE%

:RUN_AS
SET "RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_BIN="
SET "RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_="

REM Run command line
"%RUNAS_76200936_0AA1_4855_A204_05C3F3C54476_PATH_TO_TOOL%" -i:auto -l:errors "-p:%~3" "-c:%~1" -b:-10000 cmd.exe /C "%~2"
SET "EXIT_CODE=%ERRORLEVEL%"

ECHO.
IF %EXIT_CODE% EQU -10000 ECHO ##teamcity[message text='Unknown error occurred.' status='ERROR']
IF %EXIT_CODE% EQU -10001 ECHO ##teamcity[message text='Invalid usage of the tool.' status='ERROR']
IF %EXIT_CODE% EQU -10002 ECHO ##teamcity[message text='Security error occurred.' status='ERROR']
IF %EXIT_CODE% EQU -10003 ECHO ##teamcity[message text='WIN32 API error occurred.' status='ERROR']

EXIT /B %EXIT_CODE%