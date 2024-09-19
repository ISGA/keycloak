import { nodeResolve } from "@rollup/plugin-node-resolve";
import path from "node:path";
import type { RollupOptions } from "rollup";
import { defineConfig } from "rollup";

const SOURCE_DIR = "src";
const TARGET_DIR = "dist";

function defineOptions(file: string): RollupOptions {
  return {
    input: path.join(SOURCE_DIR, file),
    plugins: [nodeResolve()],
    output: [
      {
        file: path.join(TARGET_DIR, file),
      },
    ],
    external: ["@noble/hashes/sha256", "jwt-decode"],
  };
}

export default defineConfig([
  defineOptions("keycloak.js"),
  defineOptions("keycloak-authz.js"),
]);
