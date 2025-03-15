import {
  AdHocFiltersVariable,
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
  VariableValueSelectors, VizPanelExploreButton,
} from '@grafana/scenes';
import {createApiUrl, getFromApi, getWaifuDatasource, ROUTES} from '../../constants';
import {prefixRoute} from "../../utils/utils.routing";
import React from "react";
import {AdHocVariableFilter} from "@grafana/data";
import {ajax} from "rxjs/internal/ajax/ajax";
import {firstValueFrom} from "rxjs";
import {formatFilters, formatJsonFilters} from "../../utils/filters.ts";

export function modsScene() {
  const versionVariable = new QueryVariable({
    name: 'version',
    label: 'Version',
    description: 'Version to query',
    allowCustomValue: false,
    datasource: getWaifuDatasource(),
    query: 'select nspname as __value from pg_namespace where nspname like \'1.%\'',
  });

  const filterVariable = new AdHocFiltersVariable({
    name: 'filters',
    label: 'Filters',
    allowCustomValue: true,
    getTagKeysProvider: (variable, currentKey) => {
      return Promise.resolve({ replace: true, values: ['Any class name', 'Mod ID', 'Maven Coordinates', 'Authors', 'License', 'Any contained artifact', 'In pack']
            .map(v => {
                return {
                  text: v
                }
            }) });
    },
    getTagValuesProvider: (variable, filter) => {
      let query: string | undefined;
      if (filter.key === 'Mod ID' && filter.operator.endsWith('=')) {
        query = 'select distinct unnest(mods.mod_ids) from mods'
      } else if (filter.key === 'Any contained artifact' && filter.operator.endsWith('=')) {
        query = 'select distinct jsonb_path_query(mods.nested_tree, \'$.**.id\') from mods'
      } else if (filter.key === 'Authors' && filter.operator.endsWith('=')) {
        query = 'select distinct mods.authors from mods'
      } else if (filter.key === 'License' && filter.operator.endsWith('=')) {
        query = 'select distinct mods.license from mods'
      } else if (filter.key === 'Maven Coordinates' && filter.operator.endsWith('=')) {
        query = 'select distinct mods.maven_coordinates from mods'
      }

      if (query) {
        return firstValueFrom(ajax.post('/api/ds/query', {
          queries: [{
            refId: 'qr',
            format: 'table',
            datasource: getWaifuDatasource(),
            rawSql: `set session search_path to "${versionVariable.getValueText()}"; ${query}`
          }]
        }).pipe()).then((value) => {
          return {
            replace: true,
            values: (value.response as any)
                .results.qr.frames[0].data.values[0]
                .map((v: any) => {
                  return { text: v.replaceAll('"', '') }
                })
          }
        })
      }
      return Promise.resolve({ replace: true, values: [] });
    },
    expressionBuilder: filters => {
      const byName: Map<string, AdHocVariableFilter[]> = new Map<string, AdHocVariableFilter[]>()
      filters.forEach(fil => {
        if (byName.has(fil.key)) {
          byName.get(fil.key)?.push(fil)
        } else {
          byName.set(fil.key, [fil])
        }
      })

      let baseQuery = `
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
  mods`

      let group = ''

      if (byName.get('Any class name')) {
        const filtered = formatFilters('cls.name', byName.get('Any class name')!)
        baseQuery += `
join class_defs cd on cd.mod = mods.id
join classes cls on cd.type = cls.id and ${filtered}`
        // TODO - the fact that we have to manually group by each used column is a bit cursed
        group = 'group by mods.name, mods.version, mods.mod_ids, mods.license, mods.authors, mods.mod_metadata_json, mods.maven_coordinates, mods.curseforge_project_id, mods.modrinth_project_id'
      }

      baseQuery += ` where loader is false`;
      if (byName.get('Mod ID')) {
        baseQuery += ` and ${formatFilters('mods.mod_ids[1]', byName.get('Mod ID')!)}`
      }
      if (byName.get('Maven Coordinates')) {
        baseQuery += ` and ${formatFilters('mods.maven_coordinates', byName.get('Maven Coordinates')!)}`
      }
      if (byName.get('Authors')) {
        baseQuery += ` and ${formatFilters('mods.authors', byName.get('Authors')!)}`
      }
      if (byName.get('License')) {
        baseQuery += ` and ${formatFilters('mods.license', byName.get('License')!)}`
      }

      if (byName.get('In pack')) {
        const packId = byName.get('In pack')![0].value;
        interface Pack {
          mods: {
            projectId: number
            fileId: number
          }[]
        }
        const pack = getFromApi(`/platform/curseforge/pack/${packId}`) as Pack

        baseQuery += ` and (${pack.mods.map(m => `mods.curseforge_project_id = ${m.projectId}`).join(' or ')})`;
      }

      if (byName.get('Any contained artifact')) {
        baseQuery += ` and jsonb_path_exists(nested_tree, '$.**.id ? (${formatJsonFilters(byName.get('Any contained artifact')!)})')`
      }

      return baseQuery + ` ${group} order by "Mod Name"`
    }
  })

  const queryRunner = new SceneQueryRunner({
    datasource: getWaifuDatasource(),
    queries: [
      {
        refId: 'A',
        format: 'table',
        rawQuery: true,
        rawSql: createSql('${filters:text}')
      },
    ],
  });

  const modListPanel = PanelBuilders.table()
      .setTitle("Mod list")
      .setHeaderActions([new VizPanelExploreButton()])
      .applyMixin(linkProjectMixin)
      .setFilterable(true)
      .setOption('footer', {
        countRows: true,
        show: true,
        reducer: ['count']
      })
      .build()

  return new EmbeddedScene({
    $variables: new SceneVariableSet({ variables: [versionVariable, filterVariable] }),
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
                  .setHeaderActions([new VizPanelExploreButton()])
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
join mods on class_defs.mod = mods.id and ${(id as string).match(/^\d+$/) ? `curseforge_project_id = ${id}` : `modrinth_project_id = '${id}'`}
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
