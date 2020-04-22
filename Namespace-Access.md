## Namespaces in Open VSX

The [publisher](https://code.visualstudio.com/api/references/extension-manifest) field in the package.json of VS Code extensions defines a namespace in which the extension is published. The [VS Code Marketplace](https://marketplace.visualstudio.com/vscode) allows to create publishers and control who is allowed to publish.

We take a similar approach in Open VSX. The main difference is that when you create a namespace, _you are not automatically the owner of that namespace_. You must [claim ownership](#how-to-claim-a-namespace) if you want the exclusive right to publish in it.

When you [create a namespace](https://www.npmjs.com/package/ovsx#create-a-namespace), its state is declared _public_, meaning that everyone is allowed to publish in it. As soon as a user (you or somebody else) is granted ownership, the state of the namespace switches to _restricted_ and the owner obtains control on who is allowed to publish. Every extension detail page on the Open VSX website displays the state of the corresponding namespace along with the user who published the extension.

## How to Claim a Namespace

Before a namespace can be associated with your user account, you need to log in to [open-vsx.org](https://open-vsx.org).

Claiming ownership of a namespace is done publicly by creating an issue in [github.com/eclipse/open-vsx.org](https://github.com/eclipse/open-vsx.org/issues/new/choose). By this the act of granting ownership is totally transparent, and you can simply comment on an existing issue in case you want to refute a previously granted ownership.

If for some reason you do not want to request namespace ownership in public, please write to [open-vsx@typefox.io](mailto:open-vsx@typefox.io).
