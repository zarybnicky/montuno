{
  inputs.smalltt.url = github:zarybnicky/smalltt/master;
  inputs.smalltt.inputs.nixpkgs.follows = "nixpkgs";
  inputs.normbench = { url = github:zarybnicky/normalization-bench/master; flake = false; };
  inputs.seafoam.url = github:zarybnicky/seafoam/master;
  inputs.seafoam.inputs.nixpkgs.follows = "nixpkgs";
  # inputs.lean.url = github:leanprover/lean4;
  # inputs.lean.inputs.nixpkgs.follows = "nixpkgs";
  # inputs.lean.inputs.nixpkgs-vscode.follows = "nixpkgs";

  outputs = { self, smalltt, normbench, seafoam, nixpkgs }: let
    inherit (pkgs.nix-gitignore) gitignoreSourcePure;
    getSrc = dir: gitignoreSourcePure [./.gitignore] dir;
    pkgs = import nixpkgs {
      system = "x86_64-linux";
      overlays = self.overlays;
      config.allowUnfree = true;
    };
    compiler = "ghc884";
    hsPkgs = pkgs.haskell.packages.${compiler};
    graal = pkgs.graalvm11-ce;
  in {
    overlays = [
      seafoam.overlay
      smalltt.overlay
      (final: prev: {
        gradle = (prev.gradleGen.override (old: { java = graal; })).gradle_latest;
        antlr4 = prev.antlr4.override { jre = graal; };
        visualvm = prev.visualvm.override { jdk = graal; };
        kotlin-language-server = final.stdenv.mkDerivation rec {
          pname = "kotlin-language-server";
          version = "0.7.0";
          src = final.fetchzip {
            url = "https://github.com/fwcd/kotlin-language-server/releases/download/${version}/server.zip";
            sha256 = "1nsfird6mxzi2cx6k2dlvlsn3ipdf4l1grd4iwz42y3ihm8drgpa";
          };
          nativeBuildInputs = [ final.makeWrapper ];
          installPhase = ''
            install -D $src/bin/kotlin-language-server -t $out/bin
            cp -r $src/lib $out/lib
            wrapProgram $out/bin/kotlin-language-server --prefix PATH : ${final.jre}/bin
         '';
        };
      })
    ];

    devShell.x86_64-linux = hsPkgs.shellFor rec {
      withHoogle = false;
      packages = p: [ p.smalltt ];
      LD_LIBRARY_PATH = pkgs.lib.strings.makeLibraryPath buildInputs;
      FONTCONFIG_FILE = pkgs.makeFontsConf { fontDirectories = [
        pkgs.lmodern
        pkgs.xits-math
      ] ++ pkgs.texlive.tex-gyre-math.pkgs ++ pkgs.texlive.tex-gyre.pkgs ++ pkgs.texlive.Asana-Math.pkgs; };
      buildInputs = [
        hsPkgs.cabal-install
        hsPkgs.hie-bios
        hsPkgs.haskell-language-server
        pkgs.kotlin-language-server
        graal
        pkgs.gradle
        pkgs.hyperfine
        pkgs.antlr4
        pkgs.visualvm
        # (pkgs.agda.withPackages (p: [ p.standard-library ]))
        # (pkgs.idrisPackages.with-packages (with pkgs.idrisPackages; [ contrib pruviloj ]))
        # lean.defaultPackage.x86_64-linux
        # pkgs.coq
        # pkgs.dotnet-sdk-lts
        # pkgs.ocaml
        # pkgs.sbt
        # pkgs.seafoam
      ];
    };
  };
}
