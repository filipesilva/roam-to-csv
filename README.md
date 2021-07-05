# roam-to-csv

Convert a Roam Research EDN export into CSV format.

Given `./backup.edn`, creates `./backup.csv` with pages and blocks.

```bash
$ ./roam-to-csv ./backup.edn && cat backup.csv
uid,title,parent,string,order,create-time
06-28-2021,"June 28th, 2021",,,,2021-06-28T13:08:13.256Z
FsF3FaIio,,LZiyTHIGa,five,0,2021-06-28T13:08:25.145Z
KVVSsviKY,,06-28-2021,one,0,2021-06-28T13:08:15.015Z
xNUTmfMSW,,FsF3FaIio,six,0,2021-06-28T13:08:26.471Z
LZiyTHIGa,,06-28-2021,four,1,2021-06-28T13:08:23.257Z
tPEEzk_7o,,KVVSsviKY,two,0,2021-06-28T13:08:16.514Z
59okYh_ss,,KVVSsviKY,three,1,2021-06-28T13:08:18.451Z
```

You can also use it to format a Roam export for readability:
```bash
$ ./roam-to-csv --pretty-print ./backup.edn && cat backup.pp.edn
#datascript/DB {:schema
                {:create/user
                 {:db/valueType :db.type/ref,
                  :db/cardinality :db.cardinality/one},
                 :plugin/id {:db/unique :db.unique/identity},
                 :node/subpages
                 {:db/valueType :db.type/ref,
                  :db/cardinality :db.cardinality/many},
# ... lots of lines ...
                 [10 :block/string "six" 536870946]
                 [10 :block/uid "xNUTmfMSW" 536870941]
                 [10 :create/time 1624885706471 536870941]
                 [10 :create/user 3 536870941]
                 [10 :edit/time 1624885709593 536870946]
                 [10 :edit/user 3 536870941]]}
```


## Installation

You can find binaries for Linux, MacOS, and Windows in https://github.com/filipesilva/roam-to-csv/releases.

Download the archive for your respective OS, unpack it, and inside you will have a `roam-to-csv` binary you can run.

On MacOS you'll get a popup about the developer not being verified the first time you execute it.
First click "Cancel" instead of "Move to Bin" on that popup. 
Then open the "Security & Privacy" settings panel, "General" tab, and click the "Allow Anyway" button on the notice about `roam-to-csv.
Now you should be able to run it.

You can also build from source and run that code, or any modifications you add, by following the instructions below.


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

```bash
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

```bash
$ bb run-main --help
Convert a Roam Research EDN export into CSV format.
Given ./backup.edn, creates ./backup.csv with pages and blocks.

Usage:
  roam-to-csv ./backup.edn

Options:
  -h, --help          Show this message.
  -p, --pretty-print  Pretty print the EDN export only.

$ bb run-main ./backup.edn && cat backup.csv
uid,title,parent,string,order,create-time
06-28-2021,"June 28th, 2021",,,,2021-06-28T13:08:13.256Z
FsF3FaIio,,LZiyTHIGa,five,0,2021-06-28T13:08:25.145Z
KVVSsviKY,,06-28-2021,one,0,2021-06-28T13:08:15.015Z
xNUTmfMSW,,FsF3FaIio,six,0,2021-06-28T13:08:26.471Z
LZiyTHIGa,,06-28-2021,four,1,2021-06-28T13:08:23.257Z
tPEEzk_7o,,KVVSsviKY,two,0,2021-06-28T13:08:16.514Z
59okYh_ss,,KVVSsviKY,three,1,2021-06-28T13:08:18.451Z
```


### Build

To build the native image, run:

```bash
$ bb native-image
```

This should produce a binary called `roam-to-csv`.


## License

Roam-to-CSV is open source software [licensed as MIT](https://github.com/filipesilva/roam-to-csv/blob/master/LICENSE).
