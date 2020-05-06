## Namespaces in Open VSX

The [publisher](https://code.visualstudio.com/api/references/extension-manifest) field in the package.json of VS Code extensions defines a namespace in which the extension is published. The [VS Code Marketplace](https://marketplace.visualstudio.com/vscode) allows to create publishers and control who is allowed to publish.

We take a similar approach in Open VSX. The main difference is that when you create a namespace, _you are not automatically the owner of that namespace_. You must [claim ownership](#how-to-claim-a-namespace) if you want the exclusive right to publish in it.

When you [create a namespace](https://www.npmjs.com/package/ovsx#create-a-namespace), its state is declared _public_, meaning that everyone is allowed to publish in it. As soon as a user (you or somebody else) is granted ownership, the state of the namespace switches to _restricted_ and the owner obtains control on who is allowed to publish.

Every extension detail page on the Open VSX website displays the state of the corresponding namespace along with the user who published the extension. Public namespaces are marked with a [world icon](https://raw.githubusercontent.com/wiki/eclipse/openvsx/images/public-small.svg), and restricted namespaces are marked with a [shield icon](https://raw.githubusercontent.com/wiki/eclipse/openvsx/images/verified-small.svg).

## How to Claim a Namespace

Before a namespace can be associated with your user account, you need to log in to [open-vsx.org](https://open-vsx.org).

Claiming ownership of a namespace is done publicly by creating an issue in [github.com/eclipse/open-vsx.org](https://github.com/eclipse/open-vsx.org/issues/new/choose). By this the act of granting ownership is totally transparent, and you can simply comment on an existing issue in case you want to refute a previously granted ownership.

If for some reason you do not want to request namespace ownership in public, please write to [open-vsx@typefox.io](mailto:open-vsx@typefox.io).

## How to Manage Namespace Members

If you are an owner of a namespace, you are allowed to add other users to that namespace and to remove them again. This can be done in the [Namespaces](https://open-vsx.org/user-settings/namespaces) section of the settings page. There are two kinds of roles you can assign to namespace members:

 * _Owner_ &ndash; the same authority as you have
 * _Contributor_ &ndash; can publish extensions to that namespace, but cannot see or change namespace members

## The @open-vsx Account

The [@open-vsx](https://github.com/open-vsx) service account is used to publish extensions which are not (yet) published by their original maintainers. The list of published extensions is managed in the [publish-extensions](https://github.com/open-vsx/publish-extensions) repository. Most extensions on this list are in public namespaces, and they are removed from the list when a maintainer claims ownership. However, in case a namespace owner does not continue publishing extensions that are relevant to the community, these extensions can be put back to the list, and [@open-vsx](https://github.com/open-vsx) will publish them _even if their namespace is restricted_. This is an exclusive privilege of the [@open-vsx](https://github.com/open-vsx) account, and of course it should be used sparingly. A better alternative might be to ask the namespace owner to [invite another person as contributor](#how-to-manage-namespace-members) so that person can take over publishing.

## Why is a Warning Shown?

A warning icon ⚠️ is shown for some extensions, along with a hint that the publishing user is not related to the namespace of the extension. There are multiple situations that can cause this warning.

 * User _A_ published the extension while its namespace was public. Later user _B_ [claimed ownership](#how-to-claim-a-namespace) of the namespace, but did not yet publish a new version of the extension.
 * User _A_ was [invited as contributor](#how-to-manage-namespace-members) of the namespace by the owner _B_. User _A_ published the extension, but then user _B_ removed the contributor role from _A_.
 * The extension was published by [the privileged @open-vsx account](#the-open-vsx-account) although the namespace was owned by someone else.
