import React from 'react';
import {SceneApp, useSceneApp} from '@grafana/scenes';
import {AppRootProps} from '@grafana/data';
import {PluginPropsContext} from '../../utils/utils.plugin';
import {modsPage} from "../../pages/Mods/modsPage.ts";

function getSceneApp() {
  return new SceneApp({
    pages: [modsPage],
    urlSyncOptions: {
      updateUrlOnInit: true,
      createBrowserHistorySteps: true,
    },
  });
}

function AppWithScenes() {
  const scene = useSceneApp(getSceneApp);

  return (
    <>
      <scene.Component model={scene} />
    </>
  );
}

function App(props: AppRootProps) {
  return (
    <PluginPropsContext.Provider value={props}>
      <AppWithScenes />
    </PluginPropsContext.Provider>
  );
}

export default App;
