const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

// The Android project shares the repo root with the JS: keep Metro out of Gradle's build folders.
const config = {
  resolver: { blockList: [/[\/](app[\/]build|build|\.gradle|\.idea)[\/].*/] },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
