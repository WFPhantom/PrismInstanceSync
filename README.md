# Prism InstanceSync
Git hook to allow for modpack version control without moving around jar files.  
**Requires gson and toml4j**.

Prism InstanceSync is a git hook that automagically manages your modpack jar files from the JSON formatted Prism Launcher Modlist. This allows you to version control your modpack using git, without moving mod files around, neat!

Prism InstanceSync differs from the original by fully working with Prism, regardless of the vendor of the mod. I plan to eventually rewrite this to support more formats.


## To Prepare your modlist
**You need to make sure Prism Indexing is enabled, as it uses the .index folder to get the File IDs it needs for download.**

Right-click your Prism instance, press edit, go to Mods, and on the bottom right press "Export modlist". The modlist MUST be in JSON format, and it MUST include the filename. Name the file as you want, and add it in the field in the Setup.bat/Setup.sh

Currently, this only works with Prism. For Curseforge's minecraftinstance.json format, use Vazkii's InstanceSync, which is what this is forked from.

## Prism InstanceSync can
* Install itself with a simple script
* Integrate seamlessly with git so that all the following happens every time you git pull:
* Scan your modlist.json and mods folder to find work it needs to do
* Automatically download missing mod jars from Curseforge or Modrinth's CDNs
* Delete mod jars that are no longer present in the instance
* Handle .jar.disabled files, renaming them properly if you choose to enable/disable mods
* Automatically reformat the Modlist 

## How to Use

* Downoad the [latest release](https://github.com/WFPhantom/PrismInstanceSync/releases)
* Extract the InstanceSync.jar file, as well as the .bat and .sh scripts into the root of your repository
* Prepare your Modlist as described above
* Add the lines in the included .gitignore file to your repository's .gitignore
* If you are a Modpack dev, add the following lines to your setup.bat or setup.sh, depending on your OS. They go right above `java -jar InstanceSync.jar`
  - `java -cp InstanceSync.jar wfphantom.instancesync.ModlistUpdater %MODLIST%` For your batch file
  - `java -cp InstanceSync.jar wfphantom.instancesync.ModlistUpdater $MODLIST` For your shell file
* Run  setup.bat or setup.sh, whichever is appropriate for your OS
* Add something to your repository's README that tells people to run the setup script
