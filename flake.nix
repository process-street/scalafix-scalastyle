{
  description = "scalafix-scalastyle development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        jdk = pkgs.jdk17_headless;
        sbt = pkgs.sbt.override { jre = jdk; };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            sbt
            pkgs.coursier
          ];

          shellHook = ''
            export JAVA_HOME="${jdk.home}"
            export JDK_HOME="${jdk.home}"
          '';
        };
      }
    );
}
