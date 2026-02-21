# Prism InstanceSync
Git hook to allow for modpack version control without moving around files.  
**Requires gson and toml4j**.

Prism InstanceSync is a git hook that automagically manages your modpack files. This allows you to version control your modpack using git, without moving mod files around, neat!

With Prism InstanceSync's modlist, it is much easier to view mod changes in git commits, because unlike Curseforge's minecraftinstance.json, this modlist doesn't reset the order of mods every time.

Prism InstanceSync differs from the Vazkii's InstanceSync by fully working with Prism, regardless of the vendor of the mod. I plan to eventually rewrite this to support more formats. Currently, it only supports Prism, for Curseforge's minecraftinstance.json format, use the original.

## Prism InstanceSync can
* Install itself with a simple script
* Integrate seamlessly with git so that all the following happens every time you git pull:
* Scan your modlist.json and instance to find work it needs to do
* Automatically download missing mods, shaders, resourcepacks and datapacks from Curseforge or Modrinth
* Delete files that are no longer present in the instance
* Handle .disabled files, renaming them properly if you choose to enable/disable mods

## How to Use

* Downoad the [latest release](https://github.com/WFPhantom/PrismInstanceSync/releases)
* Extract the InstanceSync.jar file, as well as the .bat and .sh scripts into the root of your repository
* Add the lines in the included .gitignore file to your repository's .gitignore
* If you are a Modpack dev, the `--dev` arg will generate and update the modlist, it is suggested to add it to the script or make a new batch/shell script with it. The end-user does not need to run this arg.
* Run setup.bat or setup.sh, whichever is appropriate for your OS
* Modpack developers should change the "side" fields in the generated modlist! By default, they're grabbed from the mods, but not all mods have it set right. They do not reset once set.
* Add something to your repository's README that tells people to run the setup script

## Args
```
--dev - Generate and update the modlist.json file.
--indexDir=<path> - Specify a different directory for packwiz files relative to PrismInstanceSync.jar, useful if other launchers use packwiz, DO NOT CHANGE IF USING DEFAULT PRISM.
--option=<1-6> - Automatically selects a download option without asking the user
1: All mods, recommended if the user plans on playing on both singleplayer and multiplayer worlds.
2: Client and "Both sided" mods without Server Side only mods, recommended if the user plans on only playing on a multiplayer world. Singleplayer may differ from the multiplayer experience.
3: Server and "Both sided" mods without Client Side only mods, useful for getting the mods required to host the modpack on a server.
4: Client ONLY, useful for debugging client only mods, missing "Both sided" and Server mods.
5: Server ONLY, useful for debugging server only mods, missing "Both sided" Client mods.
6: "Both" ONLY, useful for debugging mods required on both sides without the client and server side only mods.
```