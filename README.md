<a href="https://docs.worldcoin.org/">
  <img src="https://raw.githubusercontent.com/worldcoin/world-id-docs/main/public/images/shared-readme/readme-header.png" alt="" />
</a>

# IDKit (Kotlin)

<!-- [![Kotlin Version](https://img.shields.io/endpoint?url=https%3A%2F%2Fswiftpackageindex.com%2Fapi%2Fpackages%2Fm1guelpf%2Fziggy-vapor%2Fbadge%3Ftype%3Dswift-versions&color=brightgreen)](http://swift.org)
[![docs](https://img.shields.io/badge/docs-latest-blue.svg)](https://swiftpackageindex.com/worldcoin/idkit-swift/documentation) -->

The `IDKit` library provides a simple Kotlin interface for prompting users for World ID proofs. For our Web and React Native SDKs, check out the [IDKit JS library](https://github.com/worldcoin/idkit-js).

## Usage

```kotlin
package com.worldcoin.idkit_kotlin
class IDKitTest {

    val session = Session.create(
                appID = AppID("app_ce4cb73cb75fc3b73b71ffb4de178410"),
                action = "test-action"
            )

    // Generate the connect URL (you would typically display this as a QR code)
    val connectUrl = session.connectUrl
    println("Scan this URL with the World App: $connectUrl")

    // Monitor the session status
    session.status().collect { status ->
        when (status) {
            is Status.WaitingForConnection -> {
                println("Waiting for the user to scan the QR Code")
            }
            is Status.AwaitingConfirmation -> {
                println("Awaiting user confirmation")
            }
            is Status.Confirmed -> {
                println("Got proof: ${status.proof}")
            }
            is Status.Failed -> {
                println("Got error: ${status.error.message}")
            }
        }
    }
}
```

<!-- WORLD-ID-SHARED-README-TAG:START - Do not remove or modify this section directly -->
<!-- The contents of this file are inserted to all World ID repositories to provide general context on World ID. -->

## <img align="left" width="28" height="28" src="https://raw.githubusercontent.com/worldcoin/world-id-docs/main/public/images/shared-readme/readme-world-id.png" alt="" style="margin-right: 0; padding-right: 4px;" /> About World ID

World ID is the privacy-first identity protocol that brings global proof of personhood to the internet. More on World ID in the [announcement blog post](https://worldcoin.org/blog/announcements/introducing-world-id-and-sdk).

World ID lets you seamlessly integrate authentication into your app that verifies accounts belong to real persons through [Sign in with Worldcoin](https://docs.worldcoin.org/id/sign-in). For additional flexibility and cases where you need extreme privacy, [Anonymous Actions](https://docs.worldcoin.org/id/anonymous-actions) lets you verify users in a way that cannot be tracked across verifications.

Follow the [Quick Start](https://docs.worldcoin.org/quick-start) guide for the easiest way to get started.

## 📄 Documentation

All the technical docs for the Wordcoin SDK, World ID Protocol, examples, guides can be found at https://docs.worldcoin.org/

<a href="https://docs.worldcoin.org">
  <p align="center">
    <picture align="center">
      <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/worldcoin/world-id-docs/main/public/images/shared-readme/visit-documentation-dark.png" height="50px" />
      <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/worldcoin/world-id-docs/main/public/images/shared-readme/visit-documentation-light.png" height="50px" />
      <img />
    </picture>
  </p>
</a>

<!-- WORLD-ID-SHARED-README-TAG:END -->
