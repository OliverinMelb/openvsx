## How to Publish an Extension

This guide describes publishing to the public registry at [open-vsx.org](https://open-vsx.org), which is now managed by the [Eclipse Foundation](https://www.eclipse.org/) ([see the announcement](https://blogs.eclipse.org/post/brian-king/open-vsx-registry-under-new-management)).

Similar steps may apply to registries that are hosted elsewhere, but make sure to pass the correct URL to the [ovsx](https://www.npmjs.com/package/ovsx) tool.

#### 1. Create an Eclipse account

An eclipse.org account is necessary to sign a Publisher Agreement with the Eclipse Foundation. [Use this form to register](https://accounts.eclipse.org/user/register). It is important to fill in the _GitHub Username_ field and to use exactly the same GitHub account as when you log in to open-vsx.org.

#### 2. Log in and sign the Publisher Agreement

Log in to [open-vsx.org](https://open-vsx.org) by authorizing the application with your GitHub account.

Navigate to the [Profile](https://open-vsx.org/user-settings/profile) page (click on your avatar &rarr; _Settings_). Click on _Log in with Eclipse_ and then authorize the application to access your eclipse.org account.

If the Eclipse login process is successful, you will see a button labeled _Show Publisher Agreement_ on your profile page. Click that button, read the agreement text to the bottom and click _Agree_ if you consent to publishing under these terms.

#### 3. Create an access token

Navigate to the [Access Tokens](https://open-vsx.org/user-settings/tokens) page (click on your avatar &rarr; _Settings_ &rarr; _Access Tokens_).

Click _Generate New Token_ and enter a description. We recommend to generate a new token for each environment where you want to publish, e.g. a local machine, cloud IDE, or CI build. The description will help you to identify a token in case you want to revoke it (you don't need it anymore or you suspect it has been stolen).

Click _Generate Token_ and copy the generated value to a safe place, e.g. an encrypted file or the secret variables of your cloud IDE / CI settings. Note that the value is never displayed again after you close the dialog! In case you lose a token, delete it and generate a new one.

An access token can be used to publish as many extensions as you like, until it is deleted.

#### 4. Create the namespace

The `publisher` field in your extension's package.json file defines the namespace in which the extension will be made available. You need to create the namespace in the registry before any extension can be published to it. This is done with the [ovsx](https://www.npmjs.com/package/ovsx) CLI tool. The easiest way to use it is through [npx](https://www.npmjs.com/package/npx), which makes sure you always use the latest version of the tool. Alternatively, install it globally with `npm i -g ovsx`.

Run the following command, replacing `<name>` with the value of your extension's `publisher` and replacing `<token>` with the previously generated access token value.
```
npx ovsx create-namespace <name> -p <token>
```

Creating a namespace does _not_ automatically assign you as verified owner. If you want the published extensions to be marked as _verified_, you can [claim ownership of the namespace](./Namespace-Access).

#### 5. Package and upload

The publishing process involves the two steps package and upload. Both can be done with the same [ovsx](https://www.npmjs.com/package/ovsx) CLI tool that is used to create a namespace.

If you have an already packaged `.vsix` file, you can publish it by simply running the following command, replacing `<file>` with the path to your extension package and replacing `<token>` with the previously generated access token value.
```
npx ovsx publish <file> -p <token>
```

In order to build and publish an extension from source, first make sure to prepare the project accordingly, typically by running `npm install` or `yarn`. Then run the following command in the root directory of the extension.
```
npx ovsx publish -p <token>
```

The `ovsx` tool uses [vsce](https://www.npmjs.com/package/vsce) internally to package extensions, which runs the `vscode:prepublish` script defined in the package.json as part of that process. If the extension uses [Yarn](https://yarnpkg.com) to run scripts, add the argument `--yarn`.

#### See the result

If the `ovsx` tool reported that publishing was successful, you should find your extension on [open-vsx.org](https://open-vsx.org). Please check all metadata for correctness.

-----

### GitHub Action

You can find a [GitHub action](https://docs.github.com/en/actions) that allows publishing to Open VSX at [HaaLeo/publish-vscode-extension](https://github.com/HaaLeo/publish-vscode-extension#readme).
