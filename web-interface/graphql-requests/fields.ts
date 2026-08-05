import gql from "graphql-tag";
import type {TypedDocumentNode} from "@apollo/client";
import type {
  GetFieldReferencesQuery, GetFieldReferencesQueryVariables
} from "./types/__generated__/graphql";

export const FIELD_REFERENCES: TypedDocumentNode<
    GetFieldReferencesQuery,
    GetFieldReferencesQueryVariables
> = gql`
    query GetFieldReferences($version: String!, $loader: Loader!, $class: String!, $fieldFilter: FieldPredicate!, $filter: ReferencePredicate) {
        gameVersion(loader: $loader, version: $version) {
            class(name: $class) {
                fields(where: $fieldFilter) {
                    name
                    type
                    references(where: $filter) {
                        owner {
                            mod {
                                id
                                name
                            }
                            name
                        }
                    }
                }
            }
        }
    }
`;
