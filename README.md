# roam-to-csv

Convert a Roam Research EDN export into CSV format.

Given ./backup.edn, creates ./backup.csv with pages and blocks.

TODO: release download instructions.

``` clojure
$ ./roam-to-csv ./backup.edn && cat backup.csv
uid,title,parent,string,order
06-28-2021,"June 28th, 2021"
59okYh_ss,,KVVSsviKY,three,1
KVVSsviKY,,06-28-2021,one,0
tPEEzk_7o,,KVVSsviKY,two,0
FsF3FaIio,,LZiyTHIGa,five,0
xNUTmfMSW,,FsF3FaIio,six,0
LZiyTHIGa,,06-28-2021,four,1
```


## Development

This project is based on [jayfu](https://github.com/borkdude/jayfu) and so setup instructions are pretty much copy paste.


### Prerequisites

- Download [GraalVM](https://www.graalvm.org/downloads/). Just download it to
  your Downloads folder and unzip the archive. No further installation
  required. This tutorial is based on version 21.1.0 JDK11.

- Set the `GRAALVM_HOME` environment variable. E.g.:

  `export GRAALVM_HOME=/Users/borkdude/Downloads/graalvm-ce-java11-21.1.0/Contents/Home`

- To run with Clojure or Java, you will need to have a
  Java 8 or 11 installation available. You can also use the downloaded GraalVM for this.

- Install [babashka](https://github.com/babashka/babashka#installation) (0.4.1 or higher).


#### Windows

Always use `cmd.exe` for executing GraalVM compilation, do not use PowerShell.

On Windows, install Visual Studio 2017 or 2019 and in `cmd.exe` load:

```
"C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat"
```

This will set the right environment variables for GraalVM.

After this you should be able to run `bb native-image`.


### Tasks

To see all available tasks in this project, run `bb tasks`:

``` text
$ bb tasks
The following tasks are available:

run-main     Run main
uberjar      Builds uberjar
run-uber     Run uberjar
graalvm      Checks GRAALVM_HOME env var
native-image Builds native image
```


### Run

To run this example using Clojure, run:

``` clojure
$ bb run-main --help
Convert a Roam Research EDN export into CSV format.
Given ./backup.edn, creates ./backup.csv with pages and blocks.

Usage:
  roam-to-csv ./backup.edn

Options:
  -h, --help  Show this message.

$ bb run-main ./backup.edn && cat backup.csv
uid,title,parent,string,order
06-28-2021,"June 28th, 2021"
59okYh_ss,,KVVSsviKY,three,1
KVVSsviKY,,06-28-2021,one,0
tPEEzk_7o,,KVVSsviKY,two,0
FsF3FaIio,,LZiyTHIGa,five,0
xNUTmfMSW,,FsF3FaIio,six,0
LZiyTHIGa,,06-28-2021,four,1
```


### Build

To build the native image, run:

``` text
$ bb native-image
```

This should produce a binary called `roam-to-csv`.


## License

Roam-to-CSV is open source software [licensed as MIT](https://github.com/filipesilva/roam-to-csv/blob/master/LICENSE).
