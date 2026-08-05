; SceneMax3D Windows installer
; Build this script through INNO-SETUP\build-installer.ps1 so the native
; executable, application jars, and optional signing settings are prepared first.

#ifndef MyAppName
  #define MyAppName "SceneMax3D"
#endif
#ifndef MyAppVersion
  #define MyAppVersion "2.1.0"
#endif
#ifndef MyAppPublisher
  #define MyAppPublisher "Panic Attack Games"
#endif
#ifndef MyAppURL
  #define MyAppURL "https://www.scenemax3d.com"
#endif
#ifndef MyAppExeName
  #define MyAppExeName "scenemax3d.exe"
#endif
#ifndef SourceRoot
  #define SourceRoot ".."
#endif
#define SourceRootPath AddBackslash(SourceRoot)

#ifndef WindowsExeSource
  #define WindowsExeSource SourceRootPath + "build\\native-launcher\\scenemax3d.exe"
#endif
#ifndef McpProxyJarSource
  #define McpProxyJarSource SourceRootPath + "build\\libs\\scenemax_mcp_proxy.jar"
#endif
#ifndef ProjectorArtifactsSource
  #define ProjectorArtifactsSource SourceRootPath + "out\\artifacts"
#endif
#ifndef IconSource
  #define IconSource SourceRootPath + "scenemax.ico"
#endif
#ifndef OutputDir
  #define OutputDir "Output"
#endif
#ifndef OutputBaseFilename
  #define OutputBaseFilename "scenemax3d-setup"
#endif

[Setup]
AppId={{6D48BC8A-3A5C-47A5-84E0-4E979E7F3D35}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
UninstallDisplayIcon={app}\{#MyAppExeName}
WizardStyle=modern
SetupIconFile={#IconSource}
Compression=lzma2/max
SolidCompression=yes
OutputDir={#OutputDir}
OutputBaseFilename={#OutputBaseFilename}
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
VersionInfoVersion={#MyAppVersion}
VersionInfoProductName={#MyAppName}
VersionInfoCompany={#MyAppPublisher}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#WindowsExeSource}"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#McpProxyJarSource}"; DestDir: "{app}"; DestName: "scenemax_mcp_proxy.jar"; Flags: ignoreversion
Source: "{#ProjectorArtifactsSource}\scenemax_projector-*.jar"; DestDir: "{app}\out\artifacts"; Flags: ignoreversion
Source: "..\resources-basic\resources\*"; DestDir: "{app}\resources"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\macro\*"; DestDir: "{app}\macro"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\export_targets\android_native.zip"; DestDir: "{app}\export_targets"; Flags: ignoreversion
Source: "..\tools\multiplayer-server\README.md"; DestDir: "{app}\tools\multiplayer-server"; Flags: ignoreversion
Source: "..\tools\multiplayer-server\build-servers.ps1"; DestDir: "{app}\tools\multiplayer-server"; Flags: ignoreversion
Source: "..\tools\multiplayer-server\build.zig"; DestDir: "{app}\tools\multiplayer-server"; Flags: ignoreversion
Source: "..\tools\multiplayer-server\zig\*"; DestDir: "{app}\tools\multiplayer-server\zig"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\tools\multiplayer-server\bin\*"; DestDir: "{app}\tools\multiplayer-server\bin"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\tools\multiplayer-server\load-test*.json"; DestDir: "{app}\tools\multiplayer-server"; Flags: ignoreversion
Source: "..\tools\multiplayer-server\load_test.py"; DestDir: "{app}\tools\multiplayer-server"; Flags: ignoreversion
Source: "{#IconSource}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent
