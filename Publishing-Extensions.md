## How to Publish an Extension

This guide describes publishing to the public registry at [open-vsx.org](https://open-vsx.org). The same steps apply to registries that are hosted elsewhere, but make sure to pass the correct URL to the [ovsx](https://www.npmjs.com/package/ovsx) tool.

#### 1. Create an access token

Log in to [open-vsx.org](https://open-vsx.org) and navigate to the [Access Tokens](https://open-vsx.org/user-settings/tokens) page (click on your avatar &rarr; _Settings_ &rarr; _Access Tokens_). When you log in for the first time, you need to confirm the request for personal user data to your authentication provider (GitHub).

Click _Generate New Token_ and enter a description. We recommend to generate a new token for each environment where you want to publish, e.g. a local machine, cloud IDE, or CI build. The description will help you to identify a token in case you want to revoke it (you don't need it anymore or you suspect it has been stolen).

Click _Generate Token_ and copy the generated value to a safe place, e.g. an encrypted file in your local file system or the secret variables of your cloud IDE / CI settings. Note that the value is never displayed again after you close the dialog! In case you lose a token, delete it and generate a new one.

#### 2. Create the namespace

The `publisher` field in your extension's package.json file defines the namespace in which the extension will be made available. You need to create the namespace in the registry before any extension can be published to it. This is done with the [ovsx](https://www.npmjs.com/package/ovsx) CLI tool. The easiest way to use it is through [npx](https://www.npmjs.com/package/npx), which makes sure you always use the latest version of the tool. Alternatively, install it globally with `npm i -g ovsx`.

Run the following command, replacing `<name>` with the value of your extension's `publisher` and replacing `<token>` with the previously generated access token value.
```
npx ovsx create-namespace <name> -p <token>
```

Creating a namespace does _not_ automatically give you the exclusive publishing rights. Initially, everyone will be able to publish an extension with the new namespace. If you want exclusive publishing rights, you can [claim ownership of the namespace](./Namespace-Access).

#### 3. Build and upload

If you have an already packaged `.vsix` file, you can publish it by simply running the following command, replacing `<file>` with the path to your extension package and replacing `<token>` with the previously generated access token value.
```
npx ovsx publish <file> -p <token>
```

In order to build and publish an extension from source, first make sure to prepare the project accordingly, typically by running `npm install` or `yarn`. Then run the following command in the root directory of the extension, replacing `<token>` with the previously generated access token value.
```
npx ovsx publish -p <token>
```
If the extension uses [Yarn](https://yarnpkg.com) as build tool, add the argument `--yarn`.

#### 4. See the result

If the `ovsx` tool reported that publishing was successful, you should find your extension on [open-vsx.org](https://open-vsx.org). Please check all metadata for correctness.
