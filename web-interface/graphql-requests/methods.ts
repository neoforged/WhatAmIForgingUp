import gql from "graphql-tag";
import type {TypedDocumentNode} from "@apollo/client";
import type {
  GetClassesDefiningMethodsQuery,
  GetClassesDefiningMethodsQueryVariables,
  GetMethodReferencesQuery,
  GetMethodReferencesQueryVariables,
  GetMethodsAnnotatedQuery,
  GetMethodsAnnotatedQueryVariables
} from "./types/__generated__/graphql";

export const METHOD_REFERENCES: TypedDocumentNode<
    GetMethodReferencesQuery,
    GetMethodReferencesQueryVariables
> = gql`
    query GetMethodReferences($version: String!, $loader: Loader!, $class: String!, $methodFilter: MethodPredicate!, $filter: ReferencePredicate) {
        gameVersion(loader: $loader, version: $version) {
            class(name: $class) {
                methods(where: $methodFilter) {
                    name
                    descriptor
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

export const METHODS_ANNOTATED: TypedDocumentNode<
    GetMethodsAnnotatedQuery,
    GetMethodsAnnotatedQueryVariables
> = gql`
    query GetMethodsAnnotated($version: String!, $loader: Loader!, $predicate: MethodDefinitionPredicate, $annotationPredicate: AnnotationPredicate!, $cursor: ID) {
        gameVersion(loader: $loader, version: $version) {
            methodDefinitions(
                where: {
                    allOf: [
                        {
                            anyAnnotation: $annotationPredicate
                        },
                        $predicate
                    ]
                },
                after: $cursor
            ) {
                pageInfo {
                    hasNextPage
                    endCursor
                }
                edges {
                    node {
                        name
                        descriptor
                        class {
                            mod {
                                id
                                name
                            }
                            name
                        }
                        annotations(where: $annotationPredicate) {
                            type
                            value
                        }
                    }
                }
            }
        }
    }
`;

export const CLASSES_DEFINING_METHODS: TypedDocumentNode<
    GetClassesDefiningMethodsQuery,
    GetClassesDefiningMethodsQueryVariables
> = gql`
    query GetClassesDefiningMethods($version: String!, $loader: Loader!, $methodFilter: MethodDefinitionPredicate!, $cursor: ID) {
        gameVersion(loader: $loader, version: $version) {
            classDefinitions(
                where: {anyMethod: $methodFilter}, 
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
                    }
                }
            }
        }
    }
`;
