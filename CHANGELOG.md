# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## 0.2.0
- project.clj -> deps.edn
- Refactoring archive feature
- Updated dependencies and docs

## 0.1.6
- Refactored value-X macros for greater efficiency

## 0.1.5
- Dependency and documentation updates
## 0.1.4
- Dependency updates
## 0.1.3
- Archiving is done through core.async channels, broken out into its
  own module.
- Show now takes 1-3 arguments.
- set-level! now takes 1 or 2 argements, with 1-arg implying :LogGraph
  as the `element` argument.

## 0.1.2 
- Archiving is done in a separate thread by an agent called _archiver_.

## 0.1.1 - 2020-06-03
- standard logging an graph logging are orthogonal
  - adding a :glog/message statement will be sent to std logging even
    if log-graph was never enabled.
- Fixed some errors in quotation in value-* functions.
- Timbre version upped to 4.11.0-alpha1

