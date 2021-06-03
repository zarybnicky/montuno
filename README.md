# Montuno

## Installation
### Install Nix
1. Install Nix https://nixos.org/
2. Enable Nix Flakes https://nixos.wiki/wiki/Flakes#Non-NixOS
3. Enter a develop shell `nix develop`, this might take a while, as it installs
   the entire GraalVM distribution.

### Build the application
1. Build the application `gradle installDist`
2. Enter the installation directory `cd build/install/montuno`
3. Run the launcher `bin/montuno`

(unfortunately, I have been developing the project in an IDE until the last
minute: due to Gradle issues, the executable really must be executed from that
directory)

## Usage
The launcher supports running entire scripts (`bin/montuno ../../../examples/funs.mn`),
or individual commands (`bin/montuno --normalize '(\x y.x)5'`),
or starting a REPL environment (just `bin/montuno`).

The REPL supports a large set of commands:
-  :list - lists currently loaded symbols
-  :print - prints out the elaboration state
-  :load - discards current state and loads a file
-  :reload - discards current state and reloads current file
-  :parse - prints out parse tree of an expression
-  :raw - prints out the raw inferred term and type
-  :elaborate - pretty-prints the result of elaborating an expression
-  :normalize - evaluates the expression into a normal form
-  :type - prints the inferred type
-  :normalType - normalized the inferred type
-  :builtin - loads a built-in command (:builtin ALL loads them all, see :list; fix is only available for Truffle) 
-  :engine - switches the engine between montuno-pure and montuno (Truffle-based)

There is a set of demo scripts in the examples/ directory.
