import gql from "graphql-tag";
import type {TypedDocumentNode} from "@apollo/client";
import type {GetImplementationsQuery, GetImplementationsQueryVariables} from "./types/__generated__/graphql";

export const IMPLEMENTATIONS: TypedDocumentNode<
    GetImplementationsQuery,
    GetImplementationsQueryVariables
> = gql`
    query GetImplementations($version: String!, $loader: Loader!, $class: String!) {
        gameVersion(loader: $loader, version: $version) {
            class(name: $class) {
                inheritors {
                    name
                    definitions {
                        mod {
                            id
                            name
                        }
                    }
                }
            }
        }
    }
`;
