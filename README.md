# P2P-Sync

[![Build Status](https://travis-ci.org/p2p-sync/sync.svg)](https://travis-ci.org/p2p-sync/sync)
[![Coverage Status](https://coveralls.io/repos/p2p-sync/sync/badge.svg?branch=master&service=github)](https://coveralls.io/github/p2p-sync/sync?branch=master)


P2P-Sync is a library for distributed file synchronising and sharing. It is a refined version of [Hive2Hive](https://github.com/Hive2Hive/Hive2Hive). For the underlying functionality to build the P2P network, this library
depends on [TomP2P](https://github.com/tomp2p/TomP2P).

# Features

* Synchronise a folder among all your nodes in the same network
* Share particular files or folders with other users in the network
* Profit from your own personal cloud: All file data is stored only on your peers
* Messages sent between the nodes are encrypted using RSA resp. AES
* A console based Java Client is built and waiting for you at [P2P-Sync Client](http://p2p-sync.github.io/client/)

# Requirements
* Java 8

# Install using Maven
To use this library, add the following to your `pom.xml`:

```xml

<repositories>
  <repository>
    <id>sync-mvn-repo</id>
    <url>https://raw.github.com/p2p-sync/sync/mvn-repo/</url>
    <snapshots>
      <enabled>true</enabled>
      <updatePolicy>always</updatePolicy>
    </snapshots>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>org.rmatil.sync</groupId>
    <artifactId>sync</artifactId>
    <version>0.1-SNAPSHOT</version>
  </dependency>
</dependencies>

```

# Architecture
This library uses multiple components to build the actual file synchronisation and sharing functionality. Each component
is maintained in its own repository:

* [Commons](https://github.com/p2p-sync/commons)
* [Persistence Layer](https://github.com/p2p-sync/persistence)
* [Versioning Layer](https://github.com/p2p-sync/versions)
* [Event Aggregation Layer](https://github.com/p2p-sync/aggregator)
* [Network Layer](https://github.com/p2p-sync/network)
* [Core](https://github.com/p2p-sync/sync) (this repository)
* [End-User Client](https://github.com/p2p-sync/client)
 

## Usage

For more details about particular components and examples how to use them, see the Section `Usage` in the wiki.

```java

  Path rootDir = Paths.get("path/to/synchronised/folder");
  ITreeStorageAdapter storageAdapter = new LocalStorageAdapter(rootDir);

  // initialise the synchronised folder:
  // - create folder for ObjectStore (usually .sync)
  // - create folders for shared files:
  //    - sharedWithOthers (read-write)
  //    - sharedWithOthers (read-only)
  Sync.init(storageAdapter);

  KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
  KeyPair keyPair = keyGen.genKeyPair();

  RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
  RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();


  ApplicationConfig appConfig = ApplicationConfigFactory.createBootstrapApplicationConfig(
    "Piff Jenkins",
    "ThisIsSafeUseIt",
    "SaltAndPepperMakesTheMealBetter",
    publicKey,
    privateKey,
    ApplicationConfigFactory.getDefaultIgnorePatterns()
  );

  // create a new Sync instance pointing to the root of the
  // specified storage adapter, i.e. path/to/synchronised/folder
  Sync sync = new Sync(storageAdapter);

  // start the node as bootstrap peer (depending on the specified configuration),
  // reconcile state of the synchronised folder with the ObjectStore,
  // reconcile local state with other connected clients of the same user
  NodeLocation nodeLocation = sync.connect(appConfig);

```

# License

```

  Copyright 2015 rmatil

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

```
