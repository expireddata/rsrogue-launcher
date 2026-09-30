; The rsrogue installer. Its own AppId, install folder and shortcuts, so it never touches a
; normal RuneLite install (runelite.iss is RuneLite's, kept for reference).
[Setup]
AppId={{96A1F69E-FA45-4B21-9486-C68C6F943E0E}
AppName=rsrogue
AppPublisher=rsrogue
UninstallDisplayName=rsrogue
AppVersion=${project.version}
DefaultDirName={localappdata}\rsrogue
DisableProgramGroupPage=yes

; ~30 mb for the repo the launcher downloads
ExtraDiskSpaceRequired=30000000
ArchitecturesAllowed=x64
PrivilegesRequired=lowest

WizardSmallImageFile=${project.projectDir}/innosetup/runelite_small.bmp
SetupIconFile=${project.projectDir}/innosetup/runelite.ico
UninstallDisplayIcon={app}\rsrogue.exe

Compression=lzma2
SolidCompression=yes

OutputDir=${project.projectDir}
OutputBaseFilename=rsrogueSetup

[Tasks]
Name: DesktopIcon; Description: "Create a &desktop icon";

[Files]
Source: "${project.projectDir}\build\rsrogue-win-x64\rsrogue.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "${project.projectDir}\build\rsrogue-win-x64\RuneLite.jar"; DestDir: "{app}"
Source: "${project.projectDir}\build\rsrogue-win-x64\launcher_amd64.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "${project.projectDir}\build\rsrogue-win-x64\config.json"; DestDir: "{app}"
Source: "${project.projectDir}\build\rsrogue-win-x64\jre\*"; DestDir: "{app}\jre"; Flags: recursesubdirs

[Icons]
; start menu
Name: "{userprograms}\rsrogue"; Filename: "{app}\rsrogue.exe"
Name: "{userdesktop}\rsrogue"; Filename: "{app}\rsrogue.exe"; Tasks: DesktopIcon

[Run]
Filename: "{app}\rsrogue.exe"; Parameters: "--postinstall"; Flags: nowait
Filename: "{app}\rsrogue.exe"; Description: "&Open rsrogue"; Flags: postinstall skipifsilent nowait

[InstallDelete]
; Delete the old jvm so it doesn't try to load old stuff with the new vm and crash
Type: filesandordirs; Name: "{app}\jre"

[UninstallDelete]
Type: filesandordirs; Name: "{%USERPROFILE}\.rsrogue\repository"
Type: filesandordirs; Name: "{app}"

[Code]
#include "usernamecheck.pas"
