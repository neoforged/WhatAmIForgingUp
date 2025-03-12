import {AdHocVariableFilter} from "@grafana/data";

export function formatFilters(key: string, filters: AdHocVariableFilter[]): string {
  return '(' + filters
      .map(fil => {
        if (fil.operator === '=') {
          return `${key} = '${escape(fil.value)}'`
        } else if (fil.operator === '!=') {
          return `${key} != '${escape(fil.value)}'`
        } else if (fil.operator === '=~') {
          return `${key} ~ '${escape(fil.value)}'`
        } else if (fil.operator === '!~') {
          return `${key} !~ '${escape(fil.value)}'`
        } else if (fil.operator === '>') {
          return `${key} > '${escape(fil.value)}'`
        } else if (fil.operator === '<') {
          return `${key} < '${escape(fil.value)}'`
        }
        return 'true'
      })
      .join(' or ') + ')'
}

export function formatJsonFilters(filters: AdHocVariableFilter[]): string {
  return '(' + filters
      .map(fil => {
        if (fil.operator === '=') {
          return `@ == "${escape(fil.value, '"', '\"')}"`
        } else if (fil.operator === '!=') {
          return `@ != "${escape(fil.value, '"', '\"')}"`
        } else if (fil.operator === '=~') {
          return `@ like_regex "${escape(fil.value, '"', '\"')}"`
        } else if (fil.operator === '!~') {
          return `!(@ like_regex "${escape(fil.value, '"', '\"')}")`
        } else if (fil.operator === '>') {
          return `@ > "${escape(fil.value, '"', '\"')}"`
        } else if (fil.operator === '<') {
          return `@ < "${escape(fil.value, '"', '\"')}"`
        }
        return 'true'
      })
      .join(' or ') + ')'
}

function escape(val: string, inS = "'", outS = inS.repeat(2)): string {
  return val.replaceAll(inS, outS);
}
