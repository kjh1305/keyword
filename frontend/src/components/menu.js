export const menuItems = [
    {
        id: 1,
        label: "MENU",
        isTitle: true
    },
    {
        id: 10,
        label: "유효키워드",
        icon: "bx-user",
        link: "/",
        subItems: [
            {
                id: 11,
                label: "유효키워드 추출기",
                link: "/user/validKeyword",
                parentId: 10
            },
        ]
    }
];

