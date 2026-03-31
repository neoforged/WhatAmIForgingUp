import {DefaultApolloClient} from "@vue/apollo-composable";
import type {App} from "vue";
import {ApolloClient, createHttpLink, InMemoryCache} from "@apollo/client/core";
import { setContext } from '@apollo/client/link/context';

const authLink = setContext((_, { headers }) => {
  const token = getCookieByName('discord-token');

  const newHeaders = {...headers}
  if (token) {
    newHeaders['Authorization'] = `Discord ${token}`
  }

  return {
    headers: newHeaders
  };
});

const httpLink = createHttpLink({
  uri: `${getBaseUrl()}/api/graphql`
});

const cache = new InMemoryCache();

const apolloClient = new ApolloClient({
  link: authLink.concat(httpLink),
  cache,
});

export default defineNuxtPlugin((app) => {
  app.vueApp.use({
    install(app: App) {
      app.provide(DefaultApolloClient, apolloClient);
    }
  })
})
