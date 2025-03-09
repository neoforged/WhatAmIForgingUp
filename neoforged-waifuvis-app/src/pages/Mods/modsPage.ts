import {SceneAppPage} from '@grafana/scenes';
import { prefixRoute } from '../../utils/utils.routing';
import {ROUTES} from '../../constants';
import {createDrilldown, modsScene} from "./modsScene.tsx";

export const modsPage = new SceneAppPage({
  title: 'WAIFU mods',
  url: prefixRoute(ROUTES.Mods),
  subTitle:
      'Browse mods indexed by WAIFU.',
  getScene: () => modsScene(),
  drilldowns: [
    {
      routePath: `${prefixRoute(ROUTES.Mods)}/mod/:version/:id`,
      getPage: routeMatch => createDrilldown(routeMatch)
    }
  ]
});
