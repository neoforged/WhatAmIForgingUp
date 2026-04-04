export enum ScalarType {
  STRING, BOOLEAN
}

export type FieldType =
    ScalarType | { list: FieldType } | string

export type PredicateField = {
  type: FieldType
  name: string
  hint?: string
  object?: string

  formatter?: (value: any) => string;
}

export type PredicateType = {
  fields: Record<string, PredicateField>
}

export function formatFilter(filter: any, type: FieldType): string {
  if (type == ScalarType.STRING || type == ScalarType.BOOLEAN) return filter.toString()
  else if (typeof type == 'string') {
    const fields = predicates[type as string]!!.fields
    const entry = Object.entries(filter)[0]
    if (!entry || entry.length != 2) return ''
    const field = fields[entry[0]]!!
    if (field.formatter) {
      return filter.formatter(entry[1])
    }

    if (typeof field.type == 'string') {
      return field.name + '.' + formatFilter(entry[1], field.type)
    }
    return field.name + '(' + formatFilter(entry[1], field.type) + ')'
  }

  const subType = (type as any).list as FieldType
  return (filter as any[]).map(f => formatFilter(f, subType)).join(', ')
}

export function getDefaultValue(type: FieldType): any {
  if (type == ScalarType.STRING) {
    return ""
  } else if (type == ScalarType.BOOLEAN) {
    return false
  } else if (typeof type == 'string') {
    return {}
  }
  return []
}

export const predicates: Record<string, PredicateType> = {
  PlatformProjectIdentifier: {
    fields: {
      curseforgeSlug: {
        type: ScalarType.STRING,
        name: 'CurseForge Slug',
        hint: 'Identify a CurseForge project by its slug'
      }
    }
  }
}

addLogicPredicate('StringPredicate', {
  fields: {
    equals: {
      name: 'Equals',
      type: ScalarType.STRING,
      hint: '{object} must be equal to the value below'
    },
    matches: {
      name: 'Matches RegExp',
      type: ScalarType.STRING,
      hint: '{object} must match the given regular expression'
    },
  }
})

addLogicPredicate('ModPredicate', {
  fields: {
    name: {
      type: 'StringPredicate',
      name: 'Name',
      hint: 'Test against the name of the mod',
      object: 'The name of the mod'
    },
    authors: {
      type: 'StringPredicate',
      name: 'Authors',
      hint: 'Test against the authors of the mod, sourced from the metadata file (neoforge.mods.toml)',
      object: 'The authors of the mod'
    },
    inPack: {
      type: 'PlatformProjectIdentifier',
      name: 'In modpack',
      hint: `Test whether the mod is in a modpack (mods JIJ'd by mods in the modpack are included)`
    }
  }
})

addLogicPredicate('ClassDefinitionPredicate', {
  fields: {
    mod: {
      type: 'ModPredicate',
      name: 'Mod',
      hint: 'Test against the mod of the class'
    },
    name: {
      type: 'StringPredicate',
      name: 'Name',
      hint: 'Test against the name of the class, in internal object name format (e.g. com/example/ExampleClass$SubClass)'
    }
  }
})

function addLogicPredicate(name: string, pred: PredicateType) {
  pred.fields = {
    ...pred.fields,
    anyOf: {
      name: 'Any of',
      type: {
        list: name
      },
      hint: 'At least one of the given predicates must match (logical OR)'
    },
    allOf: {
      name: 'All of',
      type: {
        list: name
      },
      hint: 'All of the given predicates must match (logical AND)'
    },
    not: {
      name: 'Not',
      type: name,
      hint: 'The given predicate must not match (logical NOT)'
    }
  }
  predicates[name] = pred
}
