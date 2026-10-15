; Inno Setup Script for HDE Studio Desktop CAD Application
; Generates standalone Windows Setup Installer (HDE-Studio-Setup-v2.1.exe)

#define MyAppName "HDE Studio"
#define MyAppVersion "2.1.0"
#define MyAppPublisher "HDE Studio / HouseDesignEngine"
#define MyAppURL "https://hdeweb.com"
#define MyAppExeName "HDE-Studio.exe"

[Setup]
AppId={{D37E7491-B780-4D2A-9464-E58309AC82B9}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
LicenseFile=D:\java-project\installer_build\EULA.txt
SetupIconFile=D:\java-project\assets\branding\hde_studio.ico
UninstallDisplayIcon={app}\hde_studio.ico
OutputDir=D:\java-project\Output
OutputBaseFilename=HDE-Studio-Setup-v2.1
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce

[Files]
; Standalone Windows launcher bat converted to exe / jar payload
Source: "D:\java-project\installer_build\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "D:\java-project\assets\branding\hde_studio.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "javaw.exe"; Parameters: "-Xms256m -Xmx2048m -jar ""{app}\HDE-Studio.jar"""; WorkingDir: "{app}"; IconFilename: "{app}\hde_studio.ico"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "javaw.exe"; Parameters: "-Xms256m -Xmx2048m -jar ""{app}\HDE-Studio.jar"""; WorkingDir: "{app}"; IconFilename: "{app}\hde_studio.ico"; Tasks: desktopicon

[Run]
Filename: "javaw.exe"; Parameters: "-Xms256m -Xmx2048m -jar ""{app}\HDE-Studio.jar"""; WorkingDir: "{app}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
