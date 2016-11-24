# TURNUS Orcc profilers
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0)

This project provides a collection of dynamic dataflow profilers suitable for the [Open RVC-CAL Compiler (Orcc)](https://github.com/orcc). The C/C++ backend and IR code interpreter are extended and enhanced with profiling capabilities.

* **Static code analysis**
* **Dynamic code analysis**
* **Dynamic code analysis**
* **Dynamic NUMA analysis**

## Building the plugins
In order to build the plugins you should clone this repository and initialize the Orcc submodule

```
git clone https://github.com/turnus/turnus.orcc.git
cd turnus.orcc
git submodule init 
git submodule update
cd orcc/eclipse/plugins
mvn install
```
Successively you can import all the projects in your Eclipse workspace making sure that required dependencies are satisfied.

### Dependencies

* turnus.analysis
* turnus.analysis.ui
* turnus.common
* turnus.bundle
* turnus.model
* net.sf.orcc.backends
* net.sf.cal
* net.sf.cal.ui
* net.sf.orcc.core
* net.sf.orcc.models
* net.sf.orcc.simulators
* net.sf.orcc.xdf.ui

## Contributors
* Simone Casale Brunet *(Maintainer, Dynamic code analysis, Dynamic code analysis, Dynamic NUMA analysis)*
* Endri Bezati  *(Dynamic code analysis, Dynamic NUMA analysis)*
* Malgorzata Michalska *(Dynamic NUMA analysis)*
* Junaid Jameel Ahmad *(Dynamic NUMA analysis)*
* Manuel Selva *(Dynamic NUMA analysis)*