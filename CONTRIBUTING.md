# How to contribute

Contributions are welcome!

* Please file bug reports and feature requests to https://github.com/metosin/reitit/issues
* For small changes, such as bug fixes or documentation changes, feel free to send a pull request.
* If you want to make a big change or implement a big new feature, please open an issue to discuss it first.

If you have questions about contributing or about reitit in general, join the [#reitit](https://clojurians.slack.com/messages/reitit/) channel in [Clojurians Slack](http://clojurians.net/).

## Environment setup

1. Clone this git repository
2. For CLJS support, install NPM dependencies: `npm install`.

## Making changes

* Fork the repository on Github
* Create a topic branch from where you want to base your work (usually the master branch)
* Check the formatting rules from existing code (no trailing whitespace, mostly default indentation)
* Ensure any new code is well-tested, and if possible, any issue fixed is covered by one or more new tests
* Verify that all tests pass using `./scripts/test.sh clj` and `./scripts/test.sh cljs`.
* Push your code to your fork of the repository
* Make a Pull Request

For more development instructions, [see the manual](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/misc/development-instructions).

## Commit messages

1. Separate subject from body with a blank line
2. Limit the subject line to 50 characters
3. Capitalize the subject line
4. Do not end the subject line with a period
5. Use the imperative mood in the subject line
    - "Add x", "Fix y", "Support z", "Remove x"
6. Wrap the body at 72 characters
7. Use the body to explain what and why vs. how
