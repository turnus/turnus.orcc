## Orcc util

## net.sf.orcc.feature
The **net.sf.orcc.feature** directory contains an eclipse-feature project for building all the latest Orcc projects. It is sued for building the Orcc nightly build plugins without waiting a release. The plugins are then stored in the eclipse p2 repository **http://www.turnus.co/addons/orcc/**

In order to build the plugins, in the Eclipse IDE follow this instructions:

1. Right click on the *net.sf.orcc.feature* project
2. *Export...* -> *Deployable features*
3. In the export wizard the following options should be selected
 - *Destination*: *Directory* should contains the output directory where the repository will be created. This directory is then uploaded on the turnus Orcc p2 site (if you do not have the access right it means that you do not need it...)
 - *Options*: Check only the following options: *Package as individual JAR archive)*, *Generate p2 repository*
 - *JAR Signing*: nothing
 - *Java Web Start*: nothing
4. That's all: the output *Directory* can then be uploaded in the eclipse p2 repository
