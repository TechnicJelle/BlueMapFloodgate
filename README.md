# BlueMap Floodgate
[![GitHub Total Downloads](https://img.shields.io/github/downloads/TechnicJelle/BlueMapFloodgate/total?label=Downloads&color=success "Click here to download the plugin")](https://github.com/TechnicJelle/BlueMapFloodgate/releases/latest)

A Minecraft Paper plugin and [BlueMap](https://github.com/BlueMap-Minecraft/BlueMap) addon that adds https://github.com/GeyserMC/Floodgate support

You can safely reload the config by reloading this plugin with [PlugManX](https://www.spigotmc.org/resources/plugmanx.88135/).\
(I do not recommend using it for other plugins! Plugin reloaders like this can seriously mess up some plugins)

### Config
- `configVersion_DO_NOT_TOUCH`
  - Do not touch this one! It's needed internally to keep track of the config versions.
- `verboseLogging: true`
  - Set to `true` if you want more messages telling you what the plugin is up to.
- `useTydiumCraftSkinAPI: true`
  - Option to choose between using the [TydiumCraft Skin API](https://tydiumcraft.net/docs/skinapi) and my own custom implementation.
- `cacheHours: 72`
  - How long to keep the playerheads cached for. The lower this number, the faster skin updates will appear, but the more network usage there will be.

## [Click here to download!](../../releases/latest)

## [TODO list](../../projects/1?fullscreen=true)

## Special thanks to
[Camotoy](https://github.com/Camotoy/GeyserSkinManager) for their open-source plugins that gave me great examples to learn from.\
And [TBlueF](https://github.com/TBlueF) of course for his amazing plugin and fast support with my silly questions!
