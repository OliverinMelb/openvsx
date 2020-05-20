VS Code has a [product.json](https://github.com/microsoft/vscode/blob/master/product.json) file that defines basic properties of the application. The schema of that file is declared in [productService.ts](https://github.com/microsoft/vscode/blob/master/src/vs/platform/product/common/productService.ts). When Microsoft build the official VS Code binaries, they add information that is not present in the open source version (see the [VS Code Wiki](https://github.com/microsoft/vscode/wiki/Differences-between-the-repository-and-Visual-Studio-Code) for more details). It is an [open question](https://github.com/microsoft/vscode/issues/31168) whether VS Code forks are allowed to use the Marketplace.

Open VSX has an adapter to the VS Code Marketplace API. In order to use this, add the following section to your `product.json` file or modify the existing `extensionsGallery` section:

```
"extensionsGallery": {
    "serviceUrl": "https://open-vsx.org/vscode/gallery",
    "itemUrl": "https://open-vsx.org/vscode/item"
}
```

The `serviceUrl` is used to search extensions, and `itemUrl` redirects to extension detail pages when you click on an extension name in VS Code. VS Code may display a warning before opening a web browser to the [open-vsx.org](https://open-vsx.org) domain. In order to suppress these warnings, either edit the trusted domains with the corresponding button in the pop-up dialog, or add the following to your `product.json`:

```
"linkProtectionTrustedDomains": [
    "https://open-vsx.org"
]
```
