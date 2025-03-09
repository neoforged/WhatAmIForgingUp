import pluginJson from './plugin.json';
import {DataSourceRef} from "@grafana/schema";
import {plugin} from "./module";

export const PLUGIN_BASE_URL = `/a/${pluginJson.id}`;

export enum ROUTES {
  Mods = 'mods',

  Home = 'home',
  WithTabs = 'page-with-tabs',
  WithDrilldown = 'page-with-drilldown',
  HelloWorld = 'hello-world',
}

export const DATASOURCE_REF = {
  uid: 'gdev-testdata',
  type: 'testdata',
};

export function getWaifuDatasource(): DataSourceRef {
  return {
    type: 'grafana-postgresql-datasource',
    uid: (plugin.meta.jsonData as any)?.datasourceId
  }
}

export function createApiUrl(path: string): string {
  const apiUrl = (plugin.meta.jsonData as any)?.apiUrl as string
  if (apiUrl.endsWith('/')) {
    return apiUrl + (path.startsWith('/') ? path.substring(1) : path)
  }
  return apiUrl + (path.startsWith('/') ? path : ('/' + path))
}

export function getFromApi(path: string): any {
  const xmlHttp = new XMLHttpRequest();
  xmlHttp.open("GET", createApiUrl(path), false);
  xmlHttp.send();
  return JSON.parse(xmlHttp.responseText);
}
