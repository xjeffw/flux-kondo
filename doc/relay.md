# Relay Lisp

rly-kondo lints Relay Lisp, the language of the [Relay](https://github.com/xjeffw/relay) editor,
in files named `*.rly`. Relay Lisp is based on Clojure, and such a file is analyzed as Clojure
except for what follows. `src/clj_kondo/impl/relay.clj` implements it, and
`test/clj_kondo/relay_test.clj` with the files of `corpus/relay` tests it. Files of other
languages are linted as clj-kondo lints them.

## What a Relay file is linted as

- **Implicit refers.** Every namespace refers the public vars of `relay.core` and
  `relay.builtins`, as if by `(:use relay.core relay.builtins)`, minus the names its
  `:refer-clojure` clause excludes. The two namespaces take the place of `clojure.core`: an
  unqualified name is a var of the namespace, of one it refers, or it is unresolved. A name
  that only Clojure has, such as `println`, is unresolved. Relay declares its special forms as
  vars of `relay.core`, so they are found the same way. A var the two namespaces have in common
  with `clojure.core` is analyzed as the Clojure one, and its arity is the one its Relay
  definition has.
- **Unknown library.** While `relay.core` or `relay.builtins` has not been linted, in this run
  or in one that cached it, nothing is reported about an unqualified name.
- **Qualified names.** A qualified name resolves in any namespace that has been linted, whether
  the file requires it or not, as Relay resolves it in any loaded namespace.
- **Namespaces in several files.** A namespace can be continued in another file with `in-ns`,
  as the generated declarations of the builtins continue the namespaces that Lisp files define.
  The cache keeps what each file contributes: linting one file replaces its part alone.
- **Macros that define vars.** A macro whose metadata has `:definition-symbol` defines, where
  it is called at the top level of a file, the var its argument at that path names:
  `{:path [0] :type :variable}` for the first argument, `{:path [0 1] :type :function}` for the
  second element of the first. The var is known once the macro is, so a call needs no
  `:lint-as`.
- **Special forms.** `(catch type name body...)` leaves `type` unevaluated. `with-read-lock` and
  `with-write-lock` bind as `let`, `set-local!` is `set!`, `future*` is `future`.
  `(after! libspec body...)` requires as its libspec says, and its body, like that of
  `on-reload!`, is evaluated later: it can name what the file defines further down, and
  redefine a var. So can `#'name`.
- **Smaller differences.** `^:private` does not keep another namespace from a var. A string
  keeps an escape the reader does not know. A vector takes an index and a default. `conj!` and
  its like change their collection in place, so their value can go unused. A file is named
  after its namespace as written, and a package keeps `P.tests.X` in `test/P/X.rly`. A
  `package.rly` that holds one map is data.

## Configuration

Configuration is clj-kondo's, in `.clj-kondo/config.edn`. In addition, the directory that the
environment variable `RLY_KONDO_LIBRARY_CONFIG_DIR` names is read as a configuration directory
before the project's, which can therefore change what it says. The Relay language server sets
it to the directory of the Relay library that holds the hooks for the library's macros.
