//! Java IDE menu parity snapshot, from assets/menu/main_menu.
//! Keep captions, hierarchy and command IDs aligned; availability is owned by chrome.
pub(crate) struct MenuEntry {
    pub(crate) name: &'static str,
    pub(crate) command: &'static str,
    pub(crate) children: &'static [MenuEntry],
}
pub(crate) const MENUS: &[MenuEntry] = &[
    MenuEntry {
        name: "File",
        command: "",
        children: &[
            MenuEntry {
                name: "Projects",
                command: "",
                children: &[
                    MenuEntry {
                        name: "New Project...",
                        command: "new_project_scripts_folder",
                        children: &[],
                    },
                    MenuEntry {
                        name: "Project Explorer...",
                        command: "project_explorer",
                        children: &[],
                    },
                    MenuEntry {
                        name: "Project Settings...",
                        command: "project_settings",
                        children: &[],
                    },
                ],
            },
            MenuEntry {
                name: "Import Program...",
                command: "load_program",
                children: &[],
            },
            MenuEntry {
                name: "Load From Cloud...",
                command: "load_from_cloud",
                children: &[],
            },
            MenuEntry {
                name: "Code Snippets...",
                command: "macro_builder",
                children: &[],
            },
            MenuEntry {
                name: "Settings...",
                command: "settings",
                children: &[],
            },
            MenuEntry {
                name: "Refresh Project Files",
                command: "refresh_project_tree",
                children: &[],
            },
            MenuEntry {
                name: "Restart",
                command: "restart_app",
                children: &[],
            },
            MenuEntry {
                name: "Open Installation Folder",
                command: "open_install_folder",
                children: &[],
            },
            MenuEntry {
                name: "Exit",
                command: "exit",
                children: &[],
            },
        ],
    },
    MenuEntry {
        name: "Assets",
        command: "",
        children: &[
            MenuEntry {
                name: "Project Inventory",
                command: "project_inventory",
                children: &[],
            },
            MenuEntry {
                name: "Create New Material...",
                command: "create_material_document",
                children: &[],
            },
            MenuEntry {
                name: "Create New Weapon...",
                command: "create_weapon_document",
                children: &[],
            },
            MenuEntry {
                name: "Create New Throw Motion...",
                command: "create_throw_motion_document",
                children: &[],
            },
            MenuEntry {
                name: "Create New IK Asset...",
                command: "create_ik_document",
                children: &[],
            },
            MenuEntry {
                name: "Create New Bevy SkyBox...",
                command: "create_skybox_document",
                children: &[],
            },
            MenuEntry {
                name: "Import Effekseer Effect...",
                command: "import_effekseer",
                children: &[],
            },
            MenuEntry {
                name: "Import Animation...",
                command: "import_animation",
                children: &[],
            },
            MenuEntry {
                name: "Import Video...",
                command: "import_video",
                children: &[],
            },
            MenuEntry {
                name: "Import Audio...",
                command: "import_audio",
                children: &[],
            },
            MenuEntry {
                name: "Import Sprite...",
                command: "add_sprite",
                children: &[],
            },
            MenuEntry {
                name: "Import 3D Model...",
                command: "add_model",
                children: &[],
            },
            MenuEntry {
                name: "Import Skybox...",
                command: "add_skybox",
                children: &[],
            },
            MenuEntry {
                name: "Open Assets Folder",
                command: "open_assets_folder",
                children: &[],
            },
        ],
    },
    MenuEntry {
        name: "Tools",
        command: "",
        children: &[
            MenuEntry {
                name: "Font Generator...",
                command: "font_generator",
                children: &[],
            },
            MenuEntry {
                name: "Model Animation Analyzer",
                command: "model_analyzer",
                children: &[],
            },
            MenuEntry {
                name: "Physics Simulation",
                command: "physics_simulation",
                children: &[],
            },
        ],
    },
    MenuEntry {
        name: "View",
        command: "",
        children: &[
            MenuEntry {
                name: "AI Console",
                command: "show_ai_console",
                children: &[],
            },
            MenuEntry {
                name: "Plugins",
                command: "plugins",
                children: &[],
            },
        ],
    },
    MenuEntry {
        name: "Git",
        command: "",
        children: &[
            MenuEntry {
                name: "Initialize Repository",
                command: "git_init",
                children: &[],
            },
            MenuEntry {
                name: "Clone Repository...",
                command: "git_clone",
                children: &[],
            },
            MenuEntry {
                name: "-",
                command: "",
                children: &[],
            },
            MenuEntry {
                name: "Stage & Commit...",
                command: "git_commit",
                children: &[],
            },
            MenuEntry {
                name: "Push...",
                command: "git_push",
                children: &[],
            },
            MenuEntry {
                name: "Pull...",
                command: "git_pull",
                children: &[],
            },
            MenuEntry {
                name: "Pull (Rebase)...",
                command: "git_pull_rebase",
                children: &[],
            },
            MenuEntry {
                name: "Fetch",
                command: "git_fetch",
                children: &[],
            },
            MenuEntry {
                name: "-",
                command: "",
                children: &[],
            },
            MenuEntry {
                name: "Branches...",
                command: "git_branches",
                children: &[],
            },
            MenuEntry {
                name: "Commit History...",
                command: "git_log",
                children: &[],
            },
            MenuEntry {
                name: "-",
                command: "",
                children: &[],
            },
            MenuEntry {
                name: "Stash Changes",
                command: "git_stash",
                children: &[],
            },
            MenuEntry {
                name: "Pop Stash",
                command: "git_stash_pop",
                children: &[],
            },
            MenuEntry {
                name: "-",
                command: "",
                children: &[],
            },
            MenuEntry {
                name: "Create .gitignore",
                command: "git_create_gitignore",
                children: &[],
            },
            MenuEntry {
                name: "Configuration...",
                command: "git_config",
                children: &[],
            },
        ],
    },
    MenuEntry {
        name: "Help",
        command: "",
        children: &[
            MenuEntry {
                name: "Online Help",
                command: "online_help",
                children: &[],
            },
            MenuEntry {
                name: "About",
                command: "about",
                children: &[],
            },
        ],
    },
];
