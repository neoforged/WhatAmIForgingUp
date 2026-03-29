export async function getUserInformation(token: string): Promise<{
  name: string
  avatar: string
}> {
  const response = await fetch('https://discord.com/api/v10/users/@me', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  })

  const user = await response.json()
  return {
    name: user.username,
    avatar: user.avatar ?
        `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.png` :
        `https://cdn.discordapp.com/embed/avatars/${!user.discriminator || user.discriminator == '0' ? (parseInt(user.id, 10) >> 22) % 5 : parseInt(user.discriminator) % 5}.png`
  }
}
