import gql from "graphql-tag";
import type {TypedDocumentNode} from "@apollo/client";
import type {
  GetClassesAnnotatedQuery,
  GetClassesAnnotatedQueryVariables,
  GetImplementationsQuery,
  GetImplementationsQueryVariables,
} from "./types/__generated__/graphql";

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

export const CLASSES_ANNOTATED: TypedDocumentNode<
    GetClassesAnnotatedQuery,
    GetClassesAnnotatedQueryVariables
> = gql`
  query GetClassesAnnotated($version: String!, $loader: Loader!, $predicate: ClassDefinitionPredicate!, $annotationPredicate: AnnotationPredicate!, $cursor: ID) {
    gameVersion(loader: $loader, version: $version) {
      classDefinitions(
        where: $predicate,
        after: $cursor
      ) {
        pageInfo {
          hasNextPage
          endCursor
        }
        edges {
          node {
            name
            mod {
              id
              name
            }
            annotations(where: $annotationPredicate) {
              value
            }
          }
        }
      }
    }
  }
`;
