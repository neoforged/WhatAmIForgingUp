import React, { useState, ChangeEvent } from 'react';
import { Button, Field, Input, useStyles2, FieldSet } from '@grafana/ui';
import { PluginConfigPageProps, AppPluginMeta, PluginMeta, GrafanaTheme2 } from '@grafana/data';
import { getBackendSrv, locationService } from '@grafana/runtime';
import { css } from '@emotion/css';
import { testIds } from '../testIds';
import { lastValueFrom } from 'rxjs';

type JsonData = {
  datasourceId?: string;
  apiUrl?: string;
};

type State = {
  datasourceId?: string;
  apiUrl?: string;
};

export interface AppConfigProps extends PluginConfigPageProps<AppPluginMeta<JsonData>> {}

const AppConfig = ({ plugin }: AppConfigProps) => {
  const s = useStyles2(getStyles);
  const { enabled, pinned, jsonData } = plugin.meta;
  const [state, setState] = useState<State>({
    datasourceId: jsonData?.datasourceId || '',
    apiUrl: jsonData?.apiUrl || ''
  });

  const isSubmitDisabled = Boolean(!state.datasourceId && !state.apiUrl);

  const onSubmit = () => {
    updatePluginAndReload(plugin.meta.id, {
      enabled,
      pinned,
      jsonData: {
        datasourceId: state.datasourceId,
        apiUrl: state.apiUrl
      }
    });
  };

  return (
    <form onSubmit={onSubmit}>
      <FieldSet label="WAIFU Settings" className={s.marginTopXl}>
        {/* Datasource ID */}
        <Field label="Datasource ID" description="The ID of the WAIFU datasource">
          <Input
            width={60}
            id="datasource-id"
            value={state?.datasourceId}
            placeholder={'Your datasource ID'}
            onChange={(event: ChangeEvent<HTMLInputElement>) => {
              setState({
                ...state,
                datasourceId: event.target.value.trim(),
              });
            }}
          />
        </Field>
        <Field label="API Url" description="Base WAIFU API URL">
          <Input
            width={60}
            id="api-url"
            value={state?.apiUrl}
            placeholder={'Your WAIFU API URL'}
            onChange={(event: ChangeEvent<HTMLInputElement>) => {
              setState({
                ...state,
                apiUrl: event.target.value.trim(),
              });
            }}
          />
        </Field>

        <div className={s.marginTop}>
          <Button type="submit" data-testid={testIds.appConfig.submit} disabled={isSubmitDisabled}>
            Save settings
          </Button>
        </div>
      </FieldSet>
    </form>
  );
};

export default AppConfig;

const getStyles = (theme: GrafanaTheme2) => ({
  colorWeak: css`
    color: ${theme.colors.text.secondary};
  `,
  marginTop: css`
    margin-top: ${theme.spacing(3)};
  `,
  marginTopXl: css`
    margin-top: ${theme.spacing(6)};
  `,
});

const updatePluginAndReload = async (pluginId: string, data: Partial<PluginMeta<JsonData>>) => {
  try {
    await updatePlugin(pluginId, data);

    // Reloading the page as the changes made here wouldn't be propagated to the actual plugin otherwise.
    // This is not ideal, however unfortunately currently there is no supported way for updating the plugin state.
    locationService.reload();
  } catch (e) {
    console.error('Error while updating the plugin', e);
  }
};

const updatePlugin = async (pluginId: string, data: Partial<PluginMeta>) => {
  const response = getBackendSrv().fetch({
    url: `/api/plugins/${pluginId}/settings`,
    method: 'POST',
    data,
  });

  const dataResponse = await lastValueFrom(response);

  return dataResponse.data;
};
