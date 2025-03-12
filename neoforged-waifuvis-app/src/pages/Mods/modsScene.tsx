import {
  EmbeddedScene,
  PanelBuilders,
  QueryVariable,
  SceneAppPage,
  SceneComponentProps,
  SceneControlsSpacer,
  SceneFlexItem,
  SceneFlexLayout,
  SceneObjectBase,
  SceneObjectState,
  SceneQueryRunner,
  SceneRefreshPicker,
  SceneRouteMatch,
  SceneVariableSet,
  VariableValueSelectors,
} from '@grafana/scenes';
import {createApiUrl, getFromApi, getWaifuDatasource, ROUTES} from '../../constants';
import {prefixRoute} from "../../utils/utils.routing";
import React from "react";

export function modsScene() {
  // Variable definition, using Grafana built-in TestData datasource
  const versionVariable = new QueryVariable({
    name: 'version',
    label: 'Version',
    description: 'Version to query',
    allowCustomValue: false,
    datasource: getWaifuDatasource(),
    query: 'select nspname as __value from pg_namespace where nspname like \'1.%\'',
  });

  const queryRunner = new SceneQueryRunner({
    datasource: getWaifuDatasource(),
    queries: [
      {
        refId: 'A',
        format: 'table',
        rawSql: createSql(`
select
  (
    case
      when mods.name = '' then 'Unknown'
      else mods.name
    end
  ) as "Mod Name",
  mods.version as "Version",
  array_to_string(mods.mod_ids, ', ') as "Mod IDs",
  mods.license as "License",
  mods.authors as "Authors",
  jsonb_path_query_first(mods.mod_metadata_json, '$.modLoader') as "Language Loader",
  mods.maven_coordinates as "Maven Coordinates",
  mods.curseforge_project_id as "CurseForge",
  mods.modrinth_project_id as "Modrinth",
  coalesce(
    mods.curseforge_project_id :: text,
    mods.modrinth_project_id
  ) as pid
from
  mods
where
  loader is false
order by
  "Mod Name"
`)
      },
    ],
  });

  const modListPanel = PanelBuilders.table()
      .setTitle(`Mod list`)
      .applyMixin(linkProjectMixin)
      .setFilterable(true)
      .setOption('footer', {
        countRows: true,
        show: true,
        reducer: ['count']
      })
      .build()

  return new EmbeddedScene({
    $variables: new SceneVariableSet({ variables: [versionVariable] }),
    $data: queryRunner,
    body: new SceneFlexLayout({
      children: [
        new SceneFlexItem({
          minHeight: 300,
          body: modListPanel,
        }),
      ],
    }),
    controls: [
      new VariableValueSelectors({}),
      new SceneControlsSpacer(),
      new SceneRefreshPicker({
        intervals: ['5s', '1m', '1h'],
        isOnCanvas: true,
      }),
    ],
  });
}

function createSql(query: string): string {
  return `
set
  session search_path to "\${version}";
${query}
`
}

function linkProjectMixin(builder: ReturnType<typeof PanelBuilders["table"]>) {
  builder.setOverrides(overrides => {
    overrides.matchFieldsWithName('pid')
        .overrideCustomFieldConfig('hidden', true)
    overrides.matchFieldsWithName('Mod Name')
        .overrideLinks([{
          title: 'Open mod',
          url: "${__url.path}/mod/${version}/${__data.fields.pid}"
        }])
    overrides.matchFieldsWithName('CurseForge')
        .overrideLinks([{
          title: 'Open mod',
          url: createApiUrl("/mod_url/${__data.fields.CurseForge}"),
          targetBlank: true
        }])
    overrides.matchFieldsWithName('Modrinth')
        .overrideLinks([{
          title: 'Open mod',
          url: createApiUrl("/mod_url/${__data.fields.Modrinth}"),
          targetBlank: true
        }])
  })
}

interface DirectElementState extends SceneObjectState {
  element: any;
}

class DirectElement extends SceneObjectBase<DirectElementState> {
  static Component = DirectElementRenderer;

  constructor(el: any) {
    super({ element: el });
  }
}

function DirectElementRenderer({ model }: SceneComponentProps<DirectElement>) {
  const { element } = model.useState();
  return (
      <>
        <div>
          {element}
        </div>
      </>
  )
}

export function createDrilldown(routeMatch: SceneRouteMatch<any>): SceneAppPage {
  const version = routeMatch.params.version
  const id = routeMatch.params.id

  interface GetModResponse {
    name: string;
    latestIndexVersion: string;
    platforms: {
      curseforge?: Platform
      modrinth?: Platform
    }
  }
  interface Platform {
    icon: string;
  }

  const modInfo = getFromApi(`/${version}/mod/${id}`) as GetModResponse

  const firstPlatform = modInfo.platforms.curseforge ?? modInfo.platforms.modrinth!

  return new SceneAppPage({
    title: 'Mod ' + modInfo.name,
    url: `${prefixRoute(ROUTES.Mods)}/mod/${version}/${id}`,
    getScene: () => {
      return new EmbeddedScene({
        body:
          new SceneFlexLayout({
            children: [
                el(
                    <>
                      <img src={firstPlatform.icon} style={{height: "100px"}} />
                      <br/>
                      Last indexed version: <code>{modInfo.latestIndexVersion}</code>
                    </>
                ),
              PanelBuilders.table()
                  .setTitle(`Mod classes`)
                  .setFilterable(true)
                  .setData(new SceneQueryRunner({
                    datasource: getWaifuDatasource(),
                    queries: [
                      {
                        refId: 'A',
                        format: 'table',
                        rawSql: `
set
  session search_path to "${version}";
select classes.name as "Class Name" from class_defs
join mods on class_defs.mod = mods.id and ${(id as string).match(/\d+/) ? `curseforge_project_id = ${id}` : `modrinth_project_id = '${id}'`}
join classes on class_defs.type = classes.id
`
                      },
                    ],
                  }))
                  .setOption('footer', {
                    countRows: true,
                    show: true,
                    reducer: ['count']
                  })
                  .build()
            ]
          })

      });
    }
  })
}

function el(element: any): SceneFlexItem {
  return new SceneFlexItem({body: new DirectElement(element)})
}
