{
  description = "javap pastebin";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      # the SDKs are prebuilt x86_64 binaries
      system = "x86_64-linux";
      sdks = import ./nix/sdks.nix { pkgs = nixpkgs.legacyPackages.${system}; };
    in
    {
      # SDK metadata, evaluated by gradle at build time. Does not require building any SDK.
      sdkMetadata = sdks.metadata;

      # SDK manifest used by the server at runtime: `nix build .#sdks -o sdk`
      packages.${system} = {
        sdks = sdks.manifest;
        default = sdks.manifest;
      };
    };
}
