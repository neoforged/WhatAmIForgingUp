# What am I Forging up
A Discord bot used to monitor API usage statistics of Minecraft mods.

### Quick Start
To get WAIFU up and running as soon as possible you will need the following:
* A Docker Installation
* A CurseForge API token (Obtainable from https://console.curseforge.com/)
* A Discord bot token (Obtainable from https://discord.com/developers/applications)

If you have all of these simply clone the repo and open a terminal window in it, 
then follow these steps:

1) Create a "run" directory in the root of the repo, copy the contents of run.template
into it, and fill out the secrets db_user.txt and db_pass.txt to create the root login
for your PostgreSQL server, you are free to use this login for WAIFU
*or create a second user for WAIFU to use* (more on that below)
2) Run ``docker compose create``
3) Run ``docker compose start db``
   1) If you wish to create a second user for WAIFU this is where you should do it
   ``docker compose exec -it db psql -U <content of root_db_user.txt> -d metabase``
   will enter you into PostgreSQL's command-line frontend inside your container to
   accomplish this, whatever you choose here will determine how you fill out the DB
   section of bot.properties, db_user.txt, and db_pass.txt
4) Run ``docker compose start meta``
   1) Following this command you must navigate to https://localhost:3000 and set up your
   metabase instance and either use your account for WAIFU *or set up a separate user*
   and use those to fill out the metabase auth section in bot.properties
5) Fill out the remainder of bot.properties with your CurseForge and Discord bot tokens
6) Run ``docker compose start waifu``
7) Done!

Now you can run ``docker compose start`` and ``docker compose stop`` to start and stop
your WAIFU instance and its related services
